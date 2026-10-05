#!/usr/bin/env bash
# Runs one test class (or method) in a JVM pinned with taskset — no perf, no Maven in the way.
# The building block for the other scripts, and useful on its own for wall-clock comparisons.
source "$(dirname "$0")/common.sh"

usage() { cat <<USAGE
usage: $(basename "$0") [-c CPUS] TestClass[#method] [-- JVM_OPTS...]

  -c CPUS   taskset CPU list, or p / e / all       (default: p = all P-cores, $P_CORES)

examples:
  $(basename "$0") VarHandleLockTest
  $(basename "$0") -c 0,1 StampedLockTest                      # one P-core, both SMT threads
  $(basename "$0") -c e VarHandleVectorTest -- -XX:-UseSuperWord
USAGE
}

parse_args "$@"
build_junit_cmd "$TARGET"
echo ">> $TARGET on CPUs $CPUS ${JAVA_OPTS[*]}" >&2
start=$(date +%s.%N)
set +e
taskset -c "$CPUS" "${JUNIT_CMD[@]}"
rc=$?
set -e
printf '>> wall clock: %.2fs on CPUs %s\n' "$(echo "$(date +%s.%N) - $start" | bc)" "$CPUS" >&2
exit $rc
