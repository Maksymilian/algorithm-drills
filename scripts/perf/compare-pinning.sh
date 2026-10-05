#!/usr/bin/env bash
# Runs the same test under several CPU placements and prints one table: the taskset experiment for
# lock and CAS tests, where what matters is whether the contending threads share a core, an L2, or
# nothing — and whether they are on P-cores or E-cores.
#
# Note: the JVM sizes Runtime.availableProcessors() from the affinity mask, so a test that sizes its
# thread pool from it runs with fewer threads on the narrow placements. Pass
# -- -XX:ActiveProcessorCount=N to hold the thread count fixed while only the CPUs change.
source "$(dirname "$0")/common.sh"

usage() { cat <<USAGE
usage: $(basename "$0") [-r N] TestClass[#method] [-- JVM_OPTS...]

  -r N   repeats per placement (default: 3)

placements (read from this machine's topology):
$(placements | column -t -s $'\t' | sed 's/^/  /')

example:
  $(basename "$0") VarHandleLockTest
  $(basename "$0") -r 5 StampedLockTest -- -XX:ActiveProcessorCount=8
USAGE
}

first_cpu() { echo "${1%%[,-]*}"; }
siblings()  { cat "/sys/devices/system/cpu/cpu$1/topology/thread_siblings_list"; }
core_id()   { cat "/sys/devices/system/cpu/cpu$1/topology/core_id"; }

# Prints "label<TAB>cpulist" lines.
placements() {
    local p0 p1 cpu
    p0="$(first_cpu "$P_CORES")"
    printf 'one P-core, SMT pair\t%s\n' "$(siblings "$p0")"
    # the first CPU of a different physical P-core
    for cpu in $(seq "$p0" 1023); do
        [[ -d /sys/devices/system/cpu/cpu$cpu ]] || break
        [[ "$(core_id "$cpu")" != "$(core_id "$p0")" ]] && { p1=$cpu; break; }
    done
    [[ -n "${p1:-}" ]] && printf 'two P-cores\t%s\n' "$p0,$p1"
    printf 'all P-cores\t%s\n' "$P_CORES"
    if [[ -n "$E_CORES" ]]; then
        local e0; e0="$(first_cpu "$E_CORES")"
        printf 'two E-cores\t%s\n' "$e0,$((e0 + 1))"
        printf 'all E-cores\t%s\n' "$E_CORES"
    fi
    printf 'everything\t%s\n' "$(cat /sys/devices/system/cpu/online)"
}

VALUE_FLAGS="-r"
parse_args "$@"
REPEAT=3
set -- "${EXTRA_ARGS[@]}"
while (( $# )); do
    case "$1" in
        -r) REPEAT="$2"; shift 2 ;;
        *) echo "unknown option $1" >&2; usage >&2; exit 2 ;;
    esac
done

require_perf 1
build_junit_cmd "$TARGET"
EVENTS="task-clock,context-switches,cpu-migrations,cycles,instructions"

# Sums an event across the hybrid PMUs (cpu_core/cycles/ + cpu_atom/cycles/), skipping
# <not counted> / <not supported> rows. perf stat -x, puts value in field 1, event in field 3.
sum_event() {
    awk -F, -v ev="$1" '
        $1 ~ /^[0-9.]+$/ { name = $3; sub(/^cpu_(core|atom)\//, "", name); sub(/\/.*$/, "", name)
                           if (name == ev) s += $1 }
        END { printf "%.0f", s }' "$2"
}

printf '%s  (%s runs each)\n\n' "$TARGET" "$REPEAT"
printf '%-22s %-10s %9s %11s %12s %11s %6s\n' placement cpus wall_s cpu_util ctx_switch migrations IPC
while IFS=$'\t' read -r label cpus; do
    csv="$OUT/compare-$$.csv"; log="$OUT/compare-${label//[ ,]/_}.log"
    start=$(date +%s.%N)
    perf stat -x, -r "$REPEAT" -e "$EVENTS" -o "$csv" -- \
        taskset -c "$cpus" "${JUNIT_CMD[@]}" > "$log" 2>&1 \
        || { printf '%-22s %-10s  FAILED — see %s\n' "$label" "$cpus" "$log"; continue; }
    wall=$(echo "($(date +%s.%N) - $start) / $REPEAT" | bc -l)
    task_ms=$(sum_event task-clock "$csv")
    ctx=$(sum_event context-switches "$csv")
    mig=$(sum_event cpu-migrations "$csv")
    cyc=$(sum_event cycles "$csv")
    ins=$(sum_event instructions "$csv")
    # task-clock is CPU-milliseconds: divided by wall time it is "how many CPUs were busy".
    util=$(echo "$task_ms / 1000 / $wall" | bc -l)
    ipc=$( (( cyc > 0 )) && echo "$ins / $cyc" | bc -l || echo 0)
    printf '%-22s %-10s %9.2f %11.2f %12d %11d %6.2f\n' "$label" "$cpus" "$wall" "$util" "$ctx" "$mig" "$ipc"
done < <(placements)
rm -f "$OUT/compare-$$.csv"
