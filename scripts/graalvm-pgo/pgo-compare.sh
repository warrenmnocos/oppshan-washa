#!/usr/bin/env bash
# scripts/graalvm-pgo/pgo-compare.sh
#
# Prints a side-by-side comparison table of the three native binaries that the native-release-pgo
# profile built and load-tested. Reads the metric files pgo-test-binary.sh wrote to
# target/comparison/.

set -euo pipefail

PROJECT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
COMPARISON_DIR="$PROJECT_DIR/target/comparison"

load_label() {
    local label="$1"
    local file="$COMPARISON_DIR/${label}.metrics"
    if [ -f "$file" ]; then
        # shellcheck disable=SC1090
        source "$file"
        eval "$(printf '%s' "$label" | tr 'a-z' 'A-Z')_SIZE=$RUNNER_SIZE"
        eval "$(printf '%s' "$label" | tr 'a-z' 'A-Z')_OK=$WORKERS_OK"
        eval "$(printf '%s' "$label" | tr 'a-z' 'A-Z')_TOTAL_ITERS=$TOTAL_ITERS"
        eval "$(printf '%s' "$label" | tr 'a-z' 'A-Z')_SLOW_COUNT=$SLOW_COUNT"
        eval "$(printf '%s' "$label" | tr 'a-z' 'A-Z')_SLOW_WORST=$SLOW_WORST_MS"
        eval "$(printf '%s' "$label" | tr 'a-z' 'A-Z')_DURATION=$DURATION_S"
    else
        echo "missing metrics for '$label' at $file" >&2
        eval "$(printf '%s' "$label" | tr 'a-z' 'A-Z')_SIZE=0"
        eval "$(printf '%s' "$label" | tr 'a-z' 'A-Z')_OK=0"
        eval "$(printf '%s' "$label" | tr 'a-z' 'A-Z')_TOTAL_ITERS=0"
        eval "$(printf '%s' "$label" | tr 'a-z' 'A-Z')_SLOW_COUNT=0"
        eval "$(printf '%s' "$label" | tr 'a-z' 'A-Z')_SLOW_WORST=0"
        eval "$(printf '%s' "$label" | tr 'a-z' 'A-Z')_DURATION=0"
    fi
}

load_label normal
load_label instrumented
load_label optimized

fmt_mb() { awk -v b="$1" 'BEGIN { printf "%.0f MB", b / 1048576 }'; }
fmt_workers() { local ok="$1" total="$2"; echo "$ok/$total"; }
fmt_iters_per_sec() {
    awk -v i="$1" -v d="$2" 'BEGIN { if (d > 0) printf "%.2f", i / d; else print "n/a" }'
}

# Compute iters/sec for each binary.
NORMAL_IPS="$(fmt_iters_per_sec "$NORMAL_TOTAL_ITERS" "$NORMAL_DURATION")"
INSTRUMENTED_IPS="$(fmt_iters_per_sec "$INSTRUMENTED_TOTAL_ITERS" "$INSTRUMENTED_DURATION")"
OPTIMIZED_IPS="$(fmt_iters_per_sec "$OPTIMIZED_TOTAL_ITERS" "$OPTIMIZED_DURATION")"

# Use printf for column alignment.
printf '\n===== PGO build comparison =====\n\n'
printf '%-28s %15s %15s %15s\n' "Metric" "Normal" "Instrumented" "Optimized"
printf '%-28s %15s %15s %15s\n' "----------------------------" "---------------" "---------------" "---------------"
printf '%-28s %15s %15s %15s\n' "Binary size"             "$(fmt_mb "$NORMAL_SIZE")"        "$(fmt_mb "$INSTRUMENTED_SIZE")"        "$(fmt_mb "$OPTIMIZED_SIZE")"
printf '%-28s %15s %15s %15s\n' "Workers ok"              "$(fmt_workers "$NORMAL_OK" "${WORKER_COUNT:-10}")" "$(fmt_workers "$INSTRUMENTED_OK" "${WORKER_COUNT:-10}")" "$(fmt_workers "$OPTIMIZED_OK" "${WORKER_COUNT:-10}")"
printf '%-28s %15s %15s %15s\n' "Total iterations"        "$NORMAL_TOTAL_ITERS"             "$INSTRUMENTED_TOTAL_ITERS"             "$OPTIMIZED_TOTAL_ITERS"
printf '%-28s %15s %15s %15s\n' "Iters/sec (all workers)" "$NORMAL_IPS"                     "$INSTRUMENTED_IPS"                     "$OPTIMIZED_IPS"
printf '%-28s %15s %15s %15s\n' "Workload duration (s)"   "$NORMAL_DURATION"                "$INSTRUMENTED_DURATION"                "$OPTIMIZED_DURATION"
printf '%-28s %15s %15s %15s\n' "Slow queries (>1s)"      "$NORMAL_SLOW_COUNT"              "$INSTRUMENTED_SLOW_COUNT"              "$OPTIMIZED_SLOW_COUNT"
printf '%-28s %15s %15s %15s\n' "Worst slow query (ms)"   "$NORMAL_SLOW_WORST"              "$INSTRUMENTED_SLOW_WORST"              "$OPTIMIZED_SLOW_WORST"
printf '\n'
