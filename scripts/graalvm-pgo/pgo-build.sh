#!/usr/bin/env bash
# scripts/graalvm-pgo/pgo-build.sh
#
# Profile-guided optimization for the native arm64 Lambda. Three stages:
#   1. Build an INSTRUMENTED native binary (--pgo-instrument).
#   2. Run it locally and drive it with workload.sh; it writes default.iprof on exit.
#   3. Build the OPTIMIZED native binary (--pgo=default.iprof) — the artifact that ships.
#
# Produces target/function.zip (the optimized Lambda artifact). Requires Docker (Dev Services
# Postgres for the running instance) and the GraalVM native toolchain.

set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$ROOT"
IPROF="$ROOT/target/washa.iprof"
LOOP_SECONDS="${LOOP_SECONDS:-90}"

echo "==> [1/3] Building instrumented native binary"
./mvnw -B -Dnative-release-pgo-instrument package -DskipTests

echo "==> [2/3] Running instrumented binary + workload to collect a profile"
# The amazon-lambda-http native binary can also serve HTTP locally for profiling.
./target/*-runner -Dquarkus.http.port=8080 &
APP_PID=$!
trap 'kill "$APP_PID" 2>/dev/null || true' EXIT

for _ in $(seq 1 30); do
  curl -sf http://localhost:8080/ >/dev/null 2>&1 && break  # washa has no /q/health; the SPA root means it's up
  sleep 2
done

LOOP_SECONDS="$LOOP_SECONDS" bash "$ROOT/scripts/graalvm-pgo/workload.sh" || true

kill "$APP_PID" 2>/dev/null || true
wait "$APP_PID" 2>/dev/null || true
trap - EXIT

# The instrumented binary writes default.iprof in its working directory on clean shutdown.
if [ -f "$ROOT/default.iprof" ]; then
  mv "$ROOT/default.iprof" "$IPROF"
elif [ -f "$ROOT/target/default.iprof" ]; then
  mv "$ROOT/target/default.iprof" "$IPROF"
fi

if [ ! -f "$IPROF" ]; then
  echo "WARN: no iprof collected; building the plain release native binary without a profile." >&2
  ./mvnw -B -Dnative-release package -DskipTests
  exit 0
fi

echo "==> [3/3] Building optimized native binary with the collected profile"
./mvnw -B -Dnative-release-pgo-optimize package -DskipTests \
  -Dpgo.iprof.path="$IPROF"

echo "==> Done. Optimized Lambda artifact: target/function.zip"
