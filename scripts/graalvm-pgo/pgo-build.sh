#!/usr/bin/env bash
# scripts/graalvm-pgo/pgo-build.sh
#
# Canonical entry point for the native-release-pgo Maven build. Wraps
# `./mvnw -P native-release-pgo` with an EXIT/INT/TERM trap that brings the docker compose stack
# down whether the build succeeds, fails, or is interrupted (Ctrl-C, SIGTERM). This is the only
# invocation path that guarantees Postgres + Keycloak are stopped and their volumes purged after the
# build, no matter how it ended.
#
# Usage:
#   bash scripts/graalvm-pgo/pgo-build.sh install -DskipTests           # full build + load tests + install
#   bash scripts/graalvm-pgo/pgo-build.sh verify -DskipTests            # what CI runs (cd.yml)
#
# The pom.xml's compose-down execution in post-integration-test handles the happy-path teardown when
# mvn is run directly (no wrapper). This wrapper adds the safety net for the failure / interruption /
# kill paths a halted build leaves behind.
#
# What is NOT caught: SIGKILL (kill -9). Bash can't trap it. If you nuke the wrapper with -9, run
# `docker compose -f scripts/graalvm-pgo/docker-compose.yml down -v` by hand.

set -uo pipefail

PROJECT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
COMPOSE_FILE="$PROJECT_DIR/scripts/graalvm-pgo/docker-compose.yml"

teardown() {
    local rc=$?
    echo "" >&2
    echo "===== pgo-build: tearing down docker compose stack (rc=$rc) =====" >&2
    docker compose -f "$COMPOSE_FILE" down -v >&2 || true
    return $rc
}
trap teardown EXIT INT TERM

cd "$PROJECT_DIR"
./mvnw -B -P native-release-pgo "$@"
