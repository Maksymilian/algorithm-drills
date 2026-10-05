#!/usr/bin/env bash
# jstack and jcmd snapshots of a test or benchmark while it runs: thread dumps with the locks each
# thread holds and waits for, heap, the JIT queue, a class histogram, native memory.
#
# Give it a test class, which it starts pinned as run-test.sh does; a JMH benchmark, which it starts
# as jmh.sh does, snapshotting the forked JVM being measured rather than the JMH host; or a running
# JVM (pid, or a pattern matched against jps -lm). Every snapshot goes to its own files under
# target/perf/snapshots/ and is summarised in one line here.
source "$(dirname "$0")/common.sh"

KINDS_ALL="threads vthreads heap jit histo nmt"

usage() { cat <<USAGE
usage: $(basename "$0") [-c CPUS] [--take WHAT] [--every MS] [--count N] TARGET [JMH_ARGS...] [-- JVM_OPTS...]

  TARGET       TestClass[#method]; a benchmark regex (anything containing "Benchmark"); or a running
               JVM, as a pid or a jps -lm pattern
  -c CPUS      pinning when this script starts the target   (default: p)
  --take WHAT  comma list of what each snapshot takes       (default: threads,heap,jit)
                 threads   jstack -l: stacks, locks held and waited for, deadlocks
                 vthreads  jcmd Thread.dump_to_file -format=json: virtual threads too, which jstack omits
                 heap      jcmd GC.heap_info
                 jit       jcmd Compiler.queue and Compiler.codecache
                 histo     jcmd GC.class_histogram, top 20 classes; runs a full GC first
                 nmt       jcmd VM.native_memory summary; starts the target with NativeMemoryTracking
               VM.command_line and VM.flags are taken once per JVM whatever you pick.
  --every MS   pause between snapshots       (default: 0 for tests, 2000 for benchmarks, 1000 attached)
  --count N    stop after N snapshots; a target started here still runs to the end   (default: no limit)

Each snapshot stops the JVM at a safepoint, and histo runs a full GC: a benchmark being
snapshotted is not being measured, so do not keep its scores.

examples:
  $(basename "$0") ReentrantLockTest                                # back to back for the whole run
  $(basename "$0") --take vthreads MapConcurrentTest                # the virtual threads mapConcurrent starts
  $(basename "$0") --take histo --count 1 'PrimitiveIntListsBenchmark.sum_boxed' -p size=1000000
  $(basename "$0") --take jit 'PrimitiveIntListsBenchmark.sum_array' -p size=1000 --every 500
  $(basename "$0") --every 5000 12345                               # a JVM you started yourself
USAGE
}

VALUE_FLAGS="--take --every --count -p -f -wi -i -w -r -t -bm -tu -prof -rf -rff -o -e -jvmArgsAppend"
parse_args "$@"
TAKE="threads,heap,jit"; EVERY=""; COUNT=0; PASS=()
set -- "${EXTRA_ARGS[@]}"
while (( $# )); do
    case "$1" in
        --take)  TAKE="$2"; shift 2 ;;
        --every) EVERY="$2"; shift 2 ;;
        --count) COUNT="$2"; shift 2 ;;
        *) PASS+=("$1"); shift ;;
    esac
done
IFS=, read -r -a KINDS <<< "$TAKE"
for kind in "${KINDS[@]}"; do
    [[ " $KINDS_ALL " == *" $kind "* ]] || { echo "unknown --take kind '$kind' (one of: $KINDS_ALL)" >&2; exit 2; }
done
taking() { [[ ",$TAKE," == *",$1,"* ]]; }

JCMD="$JAVA_HOME/bin/jcmd"
JSTACK="$JAVA_HOME/bin/jstack"

# Attach if TARGET is a pid or names a running JVM; otherwise start it, as a benchmark or a test.
if [[ "$TARGET" =~ ^[0-9]+$ ]] || "$JAVA_HOME/bin/jps" -l -m | grep -v ' Jps' | grep -q -- "$TARGET"; then
    MODE=attach
elif [[ "$TARGET" == *Benchmark* ]]; then
    MODE=jmh
else
    MODE=test
    (( ${#PASS[@]} == 0 )) || { echo "unexpected arguments for a test: ${PASS[*]}" >&2; exit 2; }
fi
case "$MODE" in
    test)   EVERY="${EVERY:-0}" ;;
    jmh)    EVERY="${EVERY:-2000}" ;;
    attach) EVERY="${EVERY:-1000}" ;;
esac

DIR="$OUT/snapshots/${TARGET//[^A-Za-z0-9_.-]/_}-$(date +%Y%m%d-%H%M%S)"
mkdir -p "$DIR"

# The first attach to a JVM has to start its attach listener: measured, 190 ms against 90 ms for every
# attach after it. Starting the listener with the JVM makes the first snapshot as quick as the rest.
LAUNCH_OPTS=(-XX:+StartAttachListener)
taking nmt && LAUNCH_OPTS+=(-XX:NativeMemoryTracking=summary)

LAUNCHED=0
case "$MODE" in
    attach)
        ROOT_PID="$(resolve_pid "$TARGET")"
        echo ">> attaching to pid $ROOT_PID" >&2 ;;
    test)
        JAVA_OPTS+=("${LAUNCH_OPTS[@]}")
        build_junit_cmd "$TARGET"
        taskset -c "$CPUS" "${JUNIT_CMD[@]}" > "$DIR/output.log" 2>&1 &
        ROOT_PID=$!; LAUNCHED=1
        echo ">> $TARGET on CPUs $CPUS, pid $ROOT_PID" >&2 ;;
    jmh)
        # jmh.sh ends in exec, so this pid becomes the JMH host; the forks are its children.
        "$(dirname "$0")/jmh.sh" -c "$CPUS" "$TARGET" "${PASS[@]}" -- "${JAVA_OPTS[@]}" "${LAUNCH_OPTS[@]}" \
            > "$DIR/output.log" 2>&1 &
        ROOT_PID=$!; LAUNCHED=1
        echo ">> $TARGET through JMH on CPUs $CPUS, host pid $ROOT_PID" >&2 ;;
esac
(( LAUNCHED )) && trap 'kill "$ROOT_PID" 2>/dev/null || true' INT TERM

# The JVM to snapshot now: under JMH the newest fork, since the host only coordinates; else the root.
current_pid() {
    local fork
    fork="$(pgrep -n -P "$ROOT_PID" -f org.openjdk.jmh.runner.ForkedMain || true)"
    if [[ -n "$fork" ]]; then
        echo "$fork"
    elif [[ "$MODE" != jmh ]] && ! tr '\0' ' ' < "/proc/$ROOT_PID/cmdline" 2>/dev/null | grep -q org.openjdk.jmh.Main; then
        echo "$ROOT_PID"
    fi
}

# One line out of whatever the snapshot took.
summarise() {
    local prefix="$1" line="" f
    f="$prefix-threads.txt"
    if [[ -s "$f" ]] && ! grep -q '^Full thread dump' "$f"; then
        line+="  no thread dump: $(head -n1 "$f" | cut -c1-60)"      # usually the JVM exiting under us
    elif [[ -s "$f" ]]; then
        local running states
        # a JMH worker runs <Benchmark>_<method>_jmhTest; a JUnit test runs on "main"
        running="$(grep -o -m1 '[A-Za-z0-9]*_[A-Za-z0-9_]*_jmhTest\.' "$f" | head -n1 | sed -E 's/^[A-Za-z0-9]*_(.*)_jmhTest\.$/\1/' || true)"
        [[ -n "$running" ]] || running="$(awk '/^"main"/{m=1} m&&/^$/{exit} m&&/at [A-Za-z0-9_.$]*Test\./{print; exit}' "$f" \
            | sed -E 's/.*at ([A-Za-z0-9_$]+\.)*([A-Za-z0-9_$]+\.[A-Za-z0-9_$]+)\([^:]*:([0-9]+)\).*/\2:\3/' || true)"
        states="$(grep -o 'java.lang.Thread.State: [A-Z_]*' "$f" | awk '{print $2}' | sort | uniq -c \
            | awk '{printf "%s%s %s", (NR>1?", ":""), $2, $1}')"
        line+="  ${running:-?}  threads $(grep -c '^"' "$f") ($states)"
        grep -q 'Found one Java-level deadlock\|Found [0-9]* deadlock' "$f" && line+="  DEADLOCK"
    fi
    f="$prefix-threads.json"
    [[ -s "$f" ]] && line+="  vthreads $(grep -c '"virtual": true' "$f" || true)"
    f="$prefix-heap.txt"
    [[ -s "$f" ]] && line+="  heap $(grep -o -m1 'used [0-9]*K' "$f" | awk '{printf "%dM", $2/1024}')"
    f="$prefix-jit.txt"
    [[ -s "$f" ]] && line+="  jit queue $(awk '/compile queue:/{q=1; next} /^$/{q=0} q&&!/Empty/{n++} END{print n+0}' "$f")"
    f="$prefix-histo.txt"
    [[ -s "$f" ]] && line+="  top $(awk '$1 ~ /^[123]:$/ {printf "%s%s %dM/%s", (n++ ? ", " : ""), $4, $3/1048576, $2}' "$f")"
    f="$prefix-nmt.txt"
    if grep -q 'committed=' "$f" 2>/dev/null; then
        line+="  native committed $(grep -o -m1 'committed=[0-9]*KB' "$f" | awk -F= '{printf "%dM", $2/1024}')"
    elif [[ -s "$f" ]]; then
        line+="  nmt off (start the JVM with -XX:NativeMemoryTracking=summary)"
    fi
    echo "$line"
}

snapshot() {
    local pid="$1" n="$2" at prefix
    at="$(printf '%6.1fs' "$(echo "$(date +%s.%N) - $START" | bc)")"
    prefix="$DIR/$(printf '%03d' "$n")-$pid"
    if [[ ! -f "$DIR/$pid-vm.txt" ]]; then
        { "$JCMD" "$pid" VM.command_line; "$JCMD" "$pid" VM.flags; } > "$DIR/$pid-vm.txt" 2>&1 || true
    fi
    for kind in "${KINDS[@]}"; do
        case "$kind" in
            threads)  "$JSTACK" -l "$pid" > "$prefix-threads.txt" 2>&1 || true ;;
            vthreads) "$JCMD" "$pid" Thread.dump_to_file -format=json "$prefix-threads.json" > /dev/null 2>&1 || true ;;
            heap)     "$JCMD" "$pid" GC.heap_info > "$prefix-heap.txt" 2>&1 || true ;;
            jit)      { "$JCMD" "$pid" Compiler.queue; "$JCMD" "$pid" Compiler.codecache; } > "$prefix-jit.txt" 2>&1 || true ;;
            histo)    { "$JCMD" "$pid" GC.class_histogram 2>&1 || true; } | head -n 23 > "$prefix-histo.txt" ;;
            nmt)      "$JCMD" "$pid" VM.native_memory summary > "$prefix-nmt.txt" 2>&1 || true ;;
        esac
    done
    echo ">> $at  pid $pid$(summarise "$prefix")" >&2
}

START=$(date +%s.%N)
SLEEP="$(printf '%d.%03d' $((EVERY / 1000)) $((EVERY % 1000)))"
n=0
while kill -0 "$ROOT_PID" 2>/dev/null; do
    pid="$(current_pid)"
    if [[ -z "$pid" ]]; then sleep 0.1; continue; fi      # JMH between forks, or not forked yet
    n=$((n + 1))
    snapshot "$pid" "$n"
    (( COUNT && n >= COUNT )) && break
    sleep "$SLEEP"
done

rc=0
if (( LAUNCHED )); then
    set +e; wait "$ROOT_PID"; rc=$?; set -e
    echo ">> target exited with $rc; its output is in $DIR/output.log" >&2
fi
echo ">> $n snapshot(s) in $DIR" >&2
exit $rc
