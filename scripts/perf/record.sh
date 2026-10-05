#!/usr/bin/env bash
# perf record with call graphs over a pinned test run, then perf report.
#
# Two JVM flags make the profile readable: -XX:+PreserveFramePointer keeps the frame-pointer
# chain intact through JIT-compiled frames (otherwise -g stacks stop at the first Java frame), and
# -XX:+DumpPerfMapAtExit writes /tmp/perf-<pid>.map so JIT code shows up as Java method names
# instead of hex addresses.
source "$(dirname "$0")/common.sh"

usage() { cat <<USAGE
usage: $(basename "$0") [-c CPUS] [-F HZ] [--no-report] TestClass[#method] [-- JVM_OPTS...]

  -c CPUS      taskset CPU list, or p / e / all    (default: p)
  -F HZ        sampling frequency                   (default: 999)
  --no-report  record only; open it later with: perf report -i $OUT/<name>.data

examples:
  $(basename "$0") VarHandleVectorTest#countMatchesTheScalarVersionAtEveryLength
  $(basename "$0") -c 0-3 StampedLockTest
USAGE
}

VALUE_FLAGS="-F"
parse_args "$@"
FREQ=999; REPORT=1
set -- "${EXTRA_ARGS[@]}"
while (( $# )); do
    case "$1" in
        -F) FREQ="$2"; shift 2 ;;
        --no-report) REPORT=0; shift ;;
        *) echo "unknown option $1" >&2; usage >&2; exit 2 ;;
    esac
done

require_perf 1
JAVA_OPTS=(-XX:+PreserveFramePointer -XX:+UnlockDiagnosticVMOptions -XX:+DumpPerfMapAtExit "${JAVA_OPTS[@]}")
build_junit_cmd "$TARGET"
DATA="$OUT/${TARGET//[#.]/_}.data"
echo ">> perf record -F $FREQ -g on CPUs $CPUS: $TARGET -> $DATA" >&2
perf record -F "$FREQ" -g -o "$DATA" -- taskset -c "$CPUS" "${JUNIT_CMD[@]}"

if (( REPORT )) && [[ -t 1 ]]; then
    # --no-children: rank by self time, which is where a spin loop or a hot CAS shows up.
    perf report -i "$DATA" --no-children --sort comm,dso,sym
else
    echo ">> perf report -i $DATA --no-children --sort comm,dso,sym" >&2
fi
