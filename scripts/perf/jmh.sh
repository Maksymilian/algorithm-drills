#!/usr/bin/env bash
# Runs a JMH benchmark from the test tree in a JVM pinned with taskset. JMH forks a fresh JVM per
# benchmark and the forks inherit the affinity mask, so the pin covers the measured JVMs too.
source "$(dirname "$0")/common.sh"

VALUE_FLAGS="-p -f -wi -i -w -r -t -bm -tu -prof -rf -rff -o -e -jvmArgsAppend"

usage() { cat <<USAGE
usage: $(basename "$0") [-c CPUS] BENCHMARK_REGEX [JMH_ARGS...] [-- JVM_OPTS...]

  -c CPUS     taskset CPU list, or p / e / all       (default: p = all P-cores, $P_CORES)
  JMH_ARGS    passed to JMH unchanged: -p size=1000, -f 2, -wi 1, -i 3, -prof gc, -rf json...
  JVM_OPTS    appended to every forked (measured) JVM

Run from $OUT, so a relative -rff path lands there.

examples:
  $(basename "$0") PrimitiveIntListsBenchmark
  $(basename "$0") 'PrimitiveIntListsBenchmark.sum_' -p size=1000000
  $(basename "$0") 'PrimitiveIntListsBenchmark.sum_' -- -XX:-UseSuperWord
  $(basename "$0") 'PrimitiveIntListsBenchmark.sort_' -- -XX:+UnlockDiagnosticVMOptions -XX:DisableIntrinsic=_arraySort
  $(basename "$0") PrimitiveIntListsBenchmark -prof gc -rf json -rff lists.json
USAGE
}

parse_args "$@"
ensure_built
# No DEFAULT_JAVA_OPTS: JMH copies the host JVM's flags into its forks, and each benchmark sets
# its own heap with @Fork(jvmArgsAppend).
JMH_CMD=("$JAVA" -cp "$CLASSPATH_ARG" org.openjdk.jmh.Main "$TARGET" "${EXTRA_ARGS[@]}")
if (( ${#JAVA_OPTS[@]} )); then
    JMH_CMD+=(-jvmArgsAppend "${JAVA_OPTS[*]}")
fi
echo ">> $TARGET on CPUs $CPUS ${JAVA_OPTS[*]}" >&2
cd "$OUT"
exec taskset -c "$CPUS" "${JMH_CMD[@]}"
