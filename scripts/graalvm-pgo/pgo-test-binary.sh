#!/usr/bin/env bash
# scripts/graalvm-pgo/pgo-test-binary.sh <label>
#
# Runs the native binary at target/runners/<label>-runner UNDER the AWS Lambda Runtime Interface
# Emulator (RIE), drives a workload of Function-URL invocations against it via parallel-workload.sh,
# stops it, and writes a metrics file to target/comparison/<label>.metrics. The native-release-pgo
# profile invokes this once per binary (normal / instrumented / optimized).
#
# washa ships as a quarkus-amazon-lambda-http handler — a Lambda poll loop, not an HTTP server — so
# it can't be boot-and-curl'd. RIE supplies the Lambda Runtime API locally: it launches the binary
# (which polls it) and exposes an invoke endpoint on :8080 where the workload POSTs events. For the
# instrumented run, the binary is stopped with SIGTERM (NOT KILL) from its own cwd (RUN_DIR) so the
# GraalVM PGO instrumentation flushes default.iprof there; it's then copied to the shared path baked
# into the build-optimized --pgo= argument.

set -euo pipefail

LABEL="${1:?usage: pgo-test-binary.sh <normal|instrumented|optimized>}"

PROJECT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$PROJECT_DIR"

RUNNER="$PROJECT_DIR/target/runners/${LABEL}-runner"
RUN_DIR="$PROJECT_DIR/target/pgo-run-${LABEL}"
COMPARISON_DIR="$PROJECT_DIR/target/comparison"
BINARY_LOG="$COMPARISON_DIR/${LABEL}.binary.log"
METRICS_FILE="$COMPARISON_DIR/${LABEL}.metrics"
SHARED_IPROF_DIR="$PROJECT_DIR/target/pgo-run"
INVOKE="http://localhost:8080/2015-03-31/functions/function/invocations"

mkdir -p "$RUN_DIR" "$COMPARISON_DIR" "$SHARED_IPROF_DIR"

# Acquire RIE. Honour a preinstalled $RIE_PATH (CI installs it as a step); otherwise fetch the
# official static binary into target/tools once and reuse it across the three test runs.
RIE_BIN="${RIE_PATH:-$PROJECT_DIR/target/tools/aws-lambda-rie}"
if [ ! -x "$RIE_BIN" ]; then
    echo "Fetching aws-lambda-rie -> $RIE_BIN"
    mkdir -p "$(dirname "$RIE_BIN")"
    curl -fsSLo "$RIE_BIN" https://github.com/aws/aws-lambda-runtime-interface-emulator/releases/latest/download/aws-lambda-rie
    chmod +x "$RIE_BIN"
fi

# RIE's invoke endpoint is hardcoded to :8080 — make sure nothing else holds it (a leftover dev
# server or a prior run) before we launch, or RIE panics with "address already in use".
if (echo > /dev/tcp/localhost/8080) 2>/dev/null; then
    echo "pgo-test-binary: :8080 is already in use — stop whatever holds it (dev server / prior RIE) and re-run" >&2
    exit 1
fi

# Host ports + Quarkus runtime env. The %pgo profile repoints the default OIDC tenant at the Keycloak
# realm below (hybrid, so it accepts the workload's bearer tokens) and widens the allowlist.
PG_HOST_PORT="${PG_HOST_PORT:-55432}"
KC_HOST_PORT="${KC_HOST_PORT:-8180}"
export QUARKUS_DATASOURCE_JDBC_URL="jdbc:postgresql://localhost:${PG_HOST_PORT}/oppshan"
export QUARKUS_DATASOURCE_USERNAME=postgres
export QUARKUS_DATASOURCE_PASSWORD=postgres
export QUARKUS_OIDC_AUTH_SERVER_URL="http://localhost:${KC_HOST_PORT}/realms/washa-pgo"
# Also force hybrid at runtime (it's baked in %pgo too) so the tenant accepts the workload's bearer
# tokens regardless of which binary is under test — a runtime override, no rebuild needed.
export QUARKUS_OIDC_APPLICATION_TYPE=hybrid
: "${PGO_OIDC_CLIENT_ID:=washa-pgo}"
: "${PGO_OIDC_CLIENT_SECRET:=pgo-client-secret-change-me}"
: "${TOKEN_ENCRYPTION_SECRET:=$(openssl rand -hex 32)}"
export PGO_OIDC_CLIENT_ID PGO_OIDC_CLIENT_SECRET TOKEN_ENCRYPTION_SECRET

# RIE emulates ONE Lambda execution environment and serializes invocations, so the workload is a
# single sustained stream (concurrent streams collide on RIE and most invocations fail). One stream
# also mirrors prod: a Lambda instance handles one request at a time.
WORKER_COUNT="${WORKER_COUNT:-1}"
LOOP_SECONDS="${LOOP_SECONDS:-300}"
export WORKER_COUNT LOOP_SECONDS PGO_OIDC_CLIENT_ID PGO_OIDC_CLIENT_SECRET KC_HOST_PORT

RIE_PID=""
cleanup() {
    local rc=$?
    if [ -n "$RIE_PID" ]; then
        local bin; bin="$(pgrep -P "$RIE_PID" 2>/dev/null | head -1 || true)"
        [ -n "$bin" ] && kill -TERM "$bin" 2>/dev/null || true
        kill -TERM "$RIE_PID" 2>/dev/null || true
        local i=0
        while [ $i -lt 15 ] && kill -0 "$RIE_PID" 2>/dev/null; do sleep 1; i=$((i+1)); done
        kill -KILL "$RIE_PID" 2>/dev/null || true
    fi
    # docker compose teardown is owned by pgo-build.sh's trap + the pom's post-integration-test step.
    return $rc
}
trap cleanup EXIT INT TERM

echo "===== Testing '${LABEL}' binary (via RIE) ====="
[ -x "$RUNNER" ] || { echo "runner not found at $RUNNER" >&2; exit 1; }
RUNNER_SIZE="$(wc -c < "$RUNNER" | tr -d ' ')"
echo "Runner: $RUNNER ($RUNNER_SIZE bytes)"

# Launch the binary under RIE from RUN_DIR so the instrumented iprof flushes there. RIE provides the
# Runtime API the binary polls and exposes the invoke endpoint on :8080.
#
# NO subshell wrapper: $! must be aws-lambda-rie itself, so `pgrep -P $RIE_PID` resolves to the washa
# runtime (RIE's direct child). At stop time we SIGTERM that runtime directly so Quarkus runs its
# shutdown sequence and GraalVM flushes default.iprof. Signalling RIE instead makes RIE SIGKILL the
# runtime (it logs "Sending SIGKILL to runtime"), which skips the flush and leaves no iprof — exactly
# the bug that produced an empty profile and failed the optimized build the first time round.
cd "$RUN_DIR"
QUARKUS_PROFILE=pgo "$RIE_BIN" "$RUNNER" \
    -Djdk.virtualThreadScheduler.parallelism="$(nproc)" \
    > "$BINARY_LOG" 2>&1 &
RIE_PID=$!
cd "$PROJECT_DIR"

# Readiness: an anonymous GET /api/me invocation comes back as a 200 HTTP response from RIE (the
# Lambda envelope inside carries 401) once the binary has booted (Flyway migrated, OIDC tenant
# initialised against Keycloak) and is polling RIE.
probe='{"version":"2.0","rawPath":"/api/me","rawQueryString":"","headers":{},"requestContext":{"http":{"method":"GET","path":"/api/me"}},"isBase64Encoded":false}'
i=0
until [ "$(curl -s -o /dev/null -w '%{http_code}' -XPOST "$INVOKE" -d "$probe" 2>/dev/null)" = "200" ]; do
    i=$((i+1)); [ $i -ge 90 ] && { echo "binary not answering via RIE in 90s" >&2; tail -30 "$BINARY_LOG" >&2; exit 1; }
    kill -0 "$RIE_PID" 2>/dev/null || { echo "RIE/binary exited during startup" >&2; tail -30 "$BINARY_LOG" >&2; exit 1; }
    sleep 1
done
echo "Binary ready (RIE invoke endpoint live)."

# Drive workload.
WORKLOAD_START=$(date +%s)
RIE_INVOKE="$INVOKE" bash "$PROJECT_DIR/scripts/graalvm-pgo/parallel-workload.sh"
WORKLOAD_END=$(date +%s)

# Stop the washa runtime (RIE's direct child) with SIGTERM so it shuts down gracefully and the
# instrumented binary flushes default.iprof to its cwd (RUN_DIR); then stop RIE. Give it room — the
# ~29 MB instrumented profile takes a couple of seconds to write.
BIN_PID="$(pgrep -P "$RIE_PID" 2>/dev/null | head -1 || true)"
if [ -n "$BIN_PID" ]; then
    kill -TERM "$BIN_PID" 2>/dev/null || true
    i=0; while [ $i -lt 20 ] && kill -0 "$BIN_PID" 2>/dev/null; do sleep 1; i=$((i+1)); done
fi
kill -TERM "$RIE_PID" 2>/dev/null || true
i=0; while [ $i -lt 10 ] && kill -0 "$RIE_PID" 2>/dev/null; do sleep 1; i=$((i+1)); done
kill -KILL "$RIE_PID" 2>/dev/null || true
RIE_PID=""

# Confirm :8080 is released before the next binary's RIE binds it.
i=0
while (echo > /dev/tcp/localhost/8080) 2>/dev/null; do
    i=$((i+1)); [ $i -ge 10 ] && { echo "WARNING: :8080 still bound 10s after stop" >&2; break; }
    sleep 1
done

# For instrumented, hand the iprof to the build-optimized execution.
if [ "$LABEL" = "instrumented" ]; then
    if [ -s "$RUN_DIR/default.iprof" ]; then
        cp "$RUN_DIR/default.iprof" "$SHARED_IPROF_DIR/default.iprof"
        echo "Captured iprof: $(wc -c < "$SHARED_IPROF_DIR/default.iprof" | tr -d ' ') bytes at $SHARED_IPROF_DIR/default.iprof"
    else
        echo "WARNING: instrumented binary produced no default.iprof — optimized build will use an empty/stale profile" >&2
    fi
fi

# Parse metrics from the worker logs parallel-workload.sh just wrote.
WORKERS_LOG_DIR="$(ls -dt /tmp/washa-pgo-workers-*/ 2>/dev/null | head -1)"
WORKERS_OK=0
TOTAL_ITERS=0
if [ -n "$WORKERS_LOG_DIR" ]; then
    for n in $(seq 1 "$WORKER_COUNT"); do
        log="$WORKERS_LOG_DIR/worker-$n.log"
        if grep -q "]: done" "$log" 2>/dev/null; then
            WORKERS_OK=$((WORKERS_OK + 1))
            iter=$(grep 'completed' "$log" | grep -oE '[0-9]+ iterations' | head -1 | grep -oE '[0-9]+' || echo 0)
            TOTAL_ITERS=$((TOTAL_ITERS + iter))
        fi
    done
fi
SLOW_COUNT="$(grep -c 'Slow query took' "$BINARY_LOG" 2>/dev/null || true)"; SLOW_COUNT="${SLOW_COUNT:-0}"
SLOW_WORST="$(grep -oE 'took [0-9]+ milliseconds' "$BINARY_LOG" 2>/dev/null | grep -oE '[0-9]+' | sort -n | tail -1 || true)"; SLOW_WORST="${SLOW_WORST:-0}"
DURATION=$((WORKLOAD_END - WORKLOAD_START))

cat > "$METRICS_FILE" <<EOF
LABEL=$LABEL
RUNNER_SIZE=$RUNNER_SIZE
WORKERS_OK=$WORKERS_OK
WORKER_COUNT=$WORKER_COUNT
TOTAL_ITERS=$TOTAL_ITERS
SLOW_COUNT=$SLOW_COUNT
SLOW_WORST_MS=$SLOW_WORST
DURATION_S=$DURATION
EOF

echo "'${LABEL}' metrics:"
sed 's/^/  /' "$METRICS_FILE"
echo "pgo-test-binary.sh: done"
