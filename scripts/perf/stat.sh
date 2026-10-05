#!/usr/bin/env bash
# perf stat over a pinned test run: hardware counters plus the scheduler events that matter for
# lock tests (context switches = parking, migrations = the scheduler moving threads around).
source "$(dirname "$0")/common.sh"

usage() { cat <<USAGE
usage: $(basename "$0") [-c CPUS] [-r N] [-e EVENTS] TestClass[#method] [-- JVM_OPTS...]

  -c CPUS    taskset CPU list, or p / e / all      (default: p)
  -r N       repeat N times, report mean ± stddev  (default: 3)
  -e EVENTS  perf event list                       (default: see below)

default events: $DEFAULT_EVENTS

examples:
  $(basename "$0") ReentrantLockTest
  $(basename "$0") -c 0,2 -r 5 VarHandleLockTest            # two physical P-cores
  $(basename "$0") -e L1-dcache-load-misses,LLC-load-misses VarHandleVectorTest
USAGE
}

DEFAULT_EVENTS="task-clock,context-switches,cpu-migrations,page-faults,cycles,instructions,branch-misses,cache-references,cache-misses"
VALUE_FLAGS="-r -e"
parse_args "$@"
REPEAT=3; EVENTS="$DEFAULT_EVENTS"
set -- "${EXTRA_ARGS[@]}"
while (( $# )); do
    case "$1" in
        -r) REPEAT="$2"; shift 2 ;;
        -e) EVENTS="$2"; shift 2 ;;
        *) echo "unknown option $1" >&2; usage >&2; exit 2 ;;
    esac
done

require_perf 1
build_junit_cmd "$TARGET"
echo ">> perf stat -r $REPEAT on CPUs $CPUS: $TARGET" >&2
# taskset inside perf: perf itself stays unpinned, the JVM (and every thread it starts) is pinned.
exec perf stat -r "$REPEAT" -e "$EVENTS" -- taskset -c "$CPUS" "${JUNIT_CMD[@]}"
