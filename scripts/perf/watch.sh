#!/usr/bin/env bash
# Live view of a JVM: counters printed every interval, or perf top.
#
# Give it a running JVM (pid, or a pattern matched against `jps -lm`), or a test class — which it
# then starts pinned and watches until it exits. Only worth it for tests that run for more than a
# few seconds; for short ones use stat.sh.
source "$(dirname "$0")/common.sh"

usage() { cat <<USAGE
usage: $(basename "$0") [-m stat|top] [-i MS] [-c CPUS] (PID | JPS_PATTERN | TestClass[#method]) [-- JVM_OPTS...]

  -m stat   counters every interval, one line per event (default)
  -m top    perf top on that process, JIT symbols refreshed from jcmd Compiler.perfmap
  -i MS     interval for -m stat, in milliseconds      (default: 1000)
  -c CPUS   pinning when this script starts the test   (default: p)

examples:
  $(basename "$0") VarHandleLockTest                         # start it pinned, print counters each second
  $(basename "$0") -m top -c 0,1 StampedLockTest
  $(basename "$0") -m top 12345                              # attach to a JVM you started yourself
  $(basename "$0") ConsoleLauncher                           # attach by jps pattern
USAGE
}

EVENTS="task-clock,context-switches,cpu-migrations,cycles,instructions,cache-misses"
VALUE_FLAGS="-m -i"
parse_args "$@"
MODE=stat; INTERVAL=1000
set -- "${EXTRA_ARGS[@]}"
while (( $# )); do
    case "$1" in
        -m) MODE="$2"; shift 2 ;;
        -i) INTERVAL="$2"; shift 2 ;;
        *) echo "unknown option $1" >&2; usage >&2; exit 2 ;;
    esac
done
require_perf 1

# Attach if TARGET is a pid or names a running JVM; otherwise treat it as a test to launch.
PID=""
if [[ "$TARGET" =~ ^[0-9]+$ ]] || "$JAVA_HOME/bin/jps" -l -m | grep -v ' Jps' | grep -q -- "$TARGET"; then
    PID="$(resolve_pid "$TARGET")"
    echo ">> attaching to pid $PID" >&2
fi

top_on_pid() {
    local pid="$1"
    # perf top resolves JIT frames from /tmp/perf-<pid>.map; refresh it as code gets compiled.
    ( while kill -0 "$pid" 2>/dev/null; do
          "$JAVA_HOME/bin/jcmd" "$pid" Compiler.perfmap >/dev/null 2>&1 || true
          sleep 5
      done ) &
    local refresher=$!
    perf top -p "$pid" --sort sym || true
    kill "$refresher" 2>/dev/null || true
}

if [[ -n "$PID" ]]; then
    case "$MODE" in
        stat) exec perf stat -I "$INTERVAL" -e "$EVENTS" -p "$PID" ;;
        top)  top_on_pid "$PID" ;;
        *) echo "unknown mode $MODE" >&2; exit 2 ;;
    esac
    exit 0
fi

build_junit_cmd "$TARGET"
case "$MODE" in
    stat)
        echo ">> perf stat every ${INTERVAL}ms on CPUs $CPUS: $TARGET" >&2
        exec perf stat -I "$INTERVAL" -e "$EVENTS" -- taskset -c "$CPUS" "${JUNIT_CMD[@]}" ;;
    top)
        taskset -c "$CPUS" "${JUNIT_CMD[@]}" > "$OUT/watch.log" 2>&1 &
        JVM=$!
        trap 'kill $JVM 2>/dev/null || true' EXIT
        echo ">> started pid $JVM on CPUs $CPUS (test output: $OUT/watch.log); q quits perf top" >&2
        sleep 1
        top_on_pid "$JVM" ;;
    *) echo "unknown mode $MODE" >&2; exit 2 ;;
esac
