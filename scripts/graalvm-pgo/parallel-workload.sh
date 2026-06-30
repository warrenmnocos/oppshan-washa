#!/usr/bin/env bash
# scripts/graalvm-pgo/parallel-workload.sh
#
# Runs WORKER_COUNT workload.sh streams against the single RIE invoke endpoint. WORKER_COUNT defaults
# to 1: RIE emulates ONE Lambda execution environment, so concurrent streams collide on it and most
# invocations fail — a single sustained stream is both reliable and the faithful prod model (a Lambda
# instance serves one request at a time). Real parallelism would need multiple RIE instances on
# separate ports, which this harness doesn't set up. The multi-worker plumbing (distinct Keycloak
# user + month key per worker, per-worker logs in /tmp/washa-pgo-workers-$$/worker-N.log, success
# gating) is kept so it could scale that way later, but leave WORKER_COUNT=1 for a single RIE.

set -uo pipefail

WORKER_COUNT="${WORKER_COUNT:-1}"
LOOP_SECONDS="${LOOP_SECONDS:-300}"
KC_USER_PREFIX="${KC_USER_PREFIX:-washa-pgo}"
KC_USER_PASSWORD="${KC_USER_PASSWORD:-tester-password}"
# Minimum workers that must finish for the run to count. Any worker contributes real samples, so by
# default one is enough; override WORKER_SUCCESS_MIN for stricter CI gating.
WORKER_SUCCESS_MIN="${WORKER_SUCCESS_MIN:-1}"

WORKER_LOG_DIR="/tmp/washa-pgo-workers-$$"
mkdir -p "$WORKER_LOG_DIR"

cleanup_parent() {
    pkill -P $$ 2>/dev/null || true
    wait 2>/dev/null || true
}
trap cleanup_parent EXIT INT TERM

# Each worker writes a distinct year_month so concurrent PUT /month invocations don't collide on
# budget_month's unique constraint. Roll the year over past December so any WORKER_COUNT is safe.
worker_month_for() {
    local n="$1"
    local year=$(( 2027 + (n - 1) / 12 ))
    local month=$(( (n - 1) % 12 + 1 ))
    printf '%04d-%02d' "$year" "$month"
}

echo "parallel-workload: spawning $WORKER_COUNT workers against RIE, LOOP_SECONDS=$LOOP_SECONDS"
echo "parallel-workload: per-worker logs in $WORKER_LOG_DIR"

declare -a PIDS=()
WORKER_SPAWN_DELAY="${WORKER_SPAWN_DELAY:-1}"
for n in $(seq 1 "$WORKER_COUNT"); do
    KC_USER="${KC_USER_PREFIX}-${n}" \
    KC_USER_PASSWORD="$KC_USER_PASSWORD" \
    WORKER_MONTH="$(worker_month_for "$n")" \
    LOOP_SECONDS="$LOOP_SECONDS" \
        bash "$(dirname "$0")/workload.sh" > "$WORKER_LOG_DIR/worker-${n}.log" 2>&1 &
    PIDS+=("$!")
    [ "$n" -lt "$WORKER_COUNT" ] && sleep "$WORKER_SPAWN_DELAY"
done

for n in $(seq 1 "$WORKER_COUNT"); do
    echo "  worker $n: pid ${PIDS[$((n-1))]}"
done

echo "parallel-workload: waiting for $WORKER_COUNT workers (up to ${LOOP_SECONDS}s + token/seed)"

FAILED=0
for n in $(seq 1 "$WORKER_COUNT"); do
    pid="${PIDS[$((n-1))]}"
    if wait "$pid"; then
        echo "  worker $n (pid $pid): ok"
    else
        rc=$?
        echo "  worker $n (pid $pid): FAILED (rc=$rc)"
        FAILED=$((FAILED + 1))
    fi
done

SUCCEEDED=$((WORKER_COUNT - FAILED))

if [ "$FAILED" -gt 0 ]; then
    echo "parallel-workload: $FAILED of $WORKER_COUNT workers failed"
    echo "parallel-workload: tail of failed-worker logs:"
    for n in $(seq 1 "$WORKER_COUNT"); do
        log="$WORKER_LOG_DIR/worker-${n}.log"
        if ! grep -q "workload\[.*\]: done" "$log" 2>/dev/null; then
            echo "--- worker $n (last 30 lines of $log) ---"
            tail -30 "$log"
        fi
    done
fi

if [ "$SUCCEEDED" -lt "$WORKER_SUCCESS_MIN" ]; then
    echo "parallel-workload: only $SUCCEEDED of $WORKER_COUNT workers succeeded (minimum $WORKER_SUCCESS_MIN) — aborting"
    exit 1
fi

echo "parallel-workload: $SUCCEEDED of $WORKER_COUNT workers completed — iprof has samples from $SUCCEEDED invocation streams"
