# Sourced by the other scripts in this directory — not meant to be run on its own.
#
# Sets up: JAVA_HOME (JDK 25), a compiled test tree, the JUnit console launcher, the P-core and
# E-core CPU lists of this hybrid CPU, and a check that perf is allowed to read counters.

set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
OUT="$ROOT/target/perf"            # perf.data, perf maps, logs — under target/, so gitignored
mkdir -p "$OUT"

# sdkman exports JAVA_HOME for its "current" JDK (17 here) and the pom needs 25, so JAVA_HOME is
# overridden rather than defaulted. Point PERF_JAVA_HOME elsewhere to use a different JDK.
export JAVA_HOME="${PERF_JAVA_HOME:-$HOME/.sdkman/candidates/java/25.0.1-tem}"
JAVA="$JAVA_HOME/bin/java"
[[ -x "$JAVA" ]] || { echo "no java at $JAVA — set JAVA_HOME to a JDK 25" >&2; exit 1; }

# Hybrid Intel: the kernel exposes each core type as its own PMU with its own CPU list.
# Falls back to "every online CPU" on a non-hybrid machine.
P_CORES="$(cat /sys/devices/cpu_core/cpus 2>/dev/null || cat /sys/devices/system/cpu/online)"
E_CORES="$(cat /sys/devices/cpu_atom/cpus 2>/dev/null || true)"

# Ta sama wersja launchera konsolowego co junit-jupiter w pom (w JUnit 6 Platform i Jupiter mają wspólny numer).
CONSOLE_VERSION=6.1.3
CONSOLE_JAR="$HOME/.m2/repository/org/junit/platform/junit-platform-console-standalone/$CONSOLE_VERSION/junit-platform-console-standalone-$CONSOLE_VERSION.jar"

# Same heap as surefire's argLine: some tests are only meaningful on a heap this small.
DEFAULT_JAVA_OPTS=(-Xmx512m)

ensure_built() {
    local stamp="$ROOT/target/test-classes"
    if [[ ! -d "$stamp" ]] \
        || [[ -n "$(find "$ROOT/src/java" "$ROOT/test/java" -newer "$stamp" -name '*.java' -print -quit)" ]]; then
        echo ">> compiling (mvn test-compile)" >&2
        (cd "$ROOT" && mvn -q test-compile) >&2
        touch "$stamp"
    fi
    if [[ ! -f "$CONSOLE_JAR" ]]; then
        echo ">> fetching junit-platform-console-standalone $CONSOLE_VERSION" >&2
        mvn -q dependency:get -Dartifact="org.junit.platform:junit-platform-console-standalone:$CONSOLE_VERSION" >&2
    fi
    CLASSPATH_ARG="$ROOT/target/classes:$ROOT/target/test-classes:$(dependency_classpath):$CONSOLE_JAR"
}

# The pom's dependency jars (fastutil, Eclipse Collections, JMH...) as one classpath, cached in $OUT
# until pom.xml or this file changes. JUnit's own jars are left out because the console jar already
# bundles the same versions.
dependency_classpath() {
    local cache="$OUT/dependency-classpath.txt"
    if [[ ! -s "$cache" || "$ROOT/pom.xml" -nt "$cache" || "${BASH_SOURCE[0]}" -nt "$cache" ]]; then
        echo ">> resolving dependencies (mvn dependency:build-classpath)" >&2
        (cd "$ROOT" && mvn -q dependency:build-classpath -Dmdep.outputFile="$cache" \
            -DexcludeGroupIds=org.junit.jupiter,org.junit.platform,org.opentest4j,org.apiguardian) >&2
    fi
    cat "$cache"
}

# "VarHandleLockTest" or "VarHandleLockTest#someMethod"; the jdk. package is added if missing.
selector_args() {
    local target="$1"
    [[ "$target" == *.* ]] || target="jdk.$target"
    if [[ "$target" == *#* ]]; then
        echo "--select-method=$target"
    else
        echo "--select-class=$target"
    fi
}

# Sets JUNIT_CMD to the command that runs one test class or method.
# Extra JVM options come from the JAVA_OPTS array set by the caller.
build_junit_cmd() {
    local target="$1"; shift
    ensure_built
    JUNIT_CMD=("$JAVA" "${DEFAULT_JAVA_OPTS[@]}" "${JAVA_OPTS[@]}" -cp "$CLASSPATH_ARG"
               org.junit.platform.console.ConsoleLauncher execute
               --disable-banner --details=summary
               "$(selector_args "$target")")
}

# Debian ships perf_event_paranoid=3, which blocks every unprivileged perf_event_open.
# 1 is what these scripts need: user+kernel counting of your own processes (the kernel side
# is where a parked lock waiter's futex time shows up).
require_perf() {
    local want="${1:-1}" have
    have="$(cat /proc/sys/kernel/perf_event_paranoid)"
    if (( have > want )); then
        cat >&2 <<MSG
perf is blocked: kernel.perf_event_paranoid is $have, these scripts need <= $want.
Enable it until the next reboot with:

    sudo sysctl -w kernel.perf_event_paranoid=$want

(add 'kernel.perf_event_paranoid = $want' to /etc/sysctl.d/99-perf.conf to keep it)
MSG
        exit 1
    fi
}

# Resolves a pid, or the pid of a running JVM whose main class/args match a pattern.
resolve_pid() {
    local arg="$1"
    if [[ "$arg" =~ ^[0-9]+$ ]]; then echo "$arg"; return; fi
    local pid
    pid="$("$JAVA_HOME/bin/jps" -l -m | grep -v -e ' Jps' -e ' sun.tools' | grep -- "$arg" | head -1 | cut -d' ' -f1)"
    [[ -n "$pid" ]] || { echo "no running JVM matches '$arg' (see: $JAVA_HOME/bin/jps -lm)" >&2; exit 1; }
    echo "$pid"
}

# Common argument parsing: [-c CPUS] TARGET [-- JVM_OPTS...]
# CPUS is a taskset list ("0-3", "0,2") or one of: p (all P-cores), e (all E-cores), all.
# Sets CPUS, TARGET, JAVA_OPTS; script-specific flags go in EXTRA_ARGS. A script whose own flags
# take a value lists them in VALUE_FLAGS (e.g. VALUE_FLAGS="-r -e") so the value stays with its flag.
parse_args() {
    CPUS="p"; TARGET=""; JAVA_OPTS=(); EXTRA_ARGS=()
    while (( $# )); do
        case "$1" in
            -c) CPUS="$2"; shift 2 ;;
            --) shift; JAVA_OPTS=("$@"); break ;;
            -h|--help) usage; exit 0 ;;
            -*) if [[ " ${VALUE_FLAGS:-} " == *" $1 "* ]]; then
                    EXTRA_ARGS+=("$1" "$2"); shift 2
                else
                    EXTRA_ARGS+=("$1"); shift
                fi ;;
            *) if [[ -z "$TARGET" ]]; then TARGET="$1"; else EXTRA_ARGS+=("$1"); fi; shift ;;
        esac
    done
    [[ -n "$TARGET" ]] || { usage >&2; exit 2; }
    case "$CPUS" in
        p) CPUS="$P_CORES" ;;
        e) [[ -n "$E_CORES" ]] || { echo "this CPU has no E-cores" >&2; exit 1; }; CPUS="$E_CORES" ;;
        all) CPUS="$(cat /sys/devices/system/cpu/online)" ;;
    esac
}
