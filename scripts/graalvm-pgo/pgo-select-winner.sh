#!/usr/bin/env bash
# scripts/graalvm-pgo/pgo-select-winner.sh
#
# Picks between the normal and optimized native binaries by total iterations (higher = more
# throughput on the same workload) and copies the winner's Lambda artifact to the canonical
# target/function.zip path, so the rest of the pipeline (cd.yml's artifact upload) ships the better
# binary. The native-release-pgo profile saved each shippable binary's zip to target/zips/<label>.zip
# at build time; this just copies the winner's. The instrumented binary is throwaway — it exists
# only to capture the iprof — so its runner is deleted.
#
# washa deploys as a Lambda, not a long-running server, so the artifact is function.zip (the native
# runner packaged as `bootstrap`), not a bare runner. That zip is built by the amazon-lambda-http
# extension during each native build; we never hand-assemble it.

set -euo pipefail

PROJECT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
RUNNERS_DIR="$PROJECT_DIR/target/runners"
ZIPS_DIR="$PROJECT_DIR/target/zips"
COMPARISON_DIR="$PROJECT_DIR/target/comparison"
TARGET_ZIP="$PROJECT_DIR/target/function.zip"

read_iters() {
    local label="$1"
    local file="$COMPARISON_DIR/${label}.metrics"
    if [ -f "$file" ]; then
        # shellcheck disable=SC1090
        source "$file"
        printf '%d' "${TOTAL_ITERS:-0}"
    else
        printf '0'
    fi
}

NORMAL_ITERS="$(read_iters normal)"
OPTIMIZED_ITERS="$(read_iters optimized)"

echo "===== Selecting winner (normal vs optimized) ====="
echo "Normal:    $NORMAL_ITERS total iterations"
echo "Optimized: $OPTIMIZED_ITERS total iterations"

if [ "$OPTIMIZED_ITERS" -gt "$NORMAL_ITERS" ]; then
    WINNER=optimized
elif [ "$NORMAL_ITERS" -gt "$OPTIMIZED_ITERS" ]; then
    WINNER=normal
else
    # Tie-breaker: prefer optimized on equal iters. PGO is the whole point — equal throughput means
    # the optimized binary is at minimum no worse, and may be better on metrics the workload didn't
    # measure (cold start, the hot paths the iprof captured).
    WINNER=optimized
    echo "(tie on total iterations — keeping optimized as the PGO-favoured default)"
fi
echo "Winner: $WINNER"

WINNER_ZIP="$ZIPS_DIR/${WINNER}.zip"
[ -f "$WINNER_ZIP" ] || { echo "winner zip missing at $WINNER_ZIP" >&2; exit 1; }

cp "$WINNER_ZIP" "$TARGET_ZIP"
echo "Copied $WINNER function.zip to $TARGET_ZIP ($(wc -c < "$TARGET_ZIP" | tr -d ' ') bytes)"

# Delete the instrumented runner (throwaway profiling artifact); it has no shippable zip.
rm -f "$RUNNERS_DIR/instrumented-runner"
echo "Deleted instrumented binary (throwaway)"

echo "pgo-select-winner.sh: done"
