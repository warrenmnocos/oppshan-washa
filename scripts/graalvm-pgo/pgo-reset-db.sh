#!/usr/bin/env bash
# scripts/graalvm-pgo/pgo-reset-db.sh
#
# Truncates the workload-mutated tables in the running Postgres container so each load test in the
# native-release-pgo comparison starts from an equivalent empty-budget state. Called from the
# native-release-pgo profile between tests:
#   test-normal       -> reset-db-after-normal       -> build-instrumented
#   test-instrumented -> reset-db-after-instrumented -> build-optimized
#
# Why TRUNCATE (vs `docker compose down -v && up -d`):
#   - <1 second per call instead of 30-60+ seconds
#   - Keeps the container running, preserving the Postgres page cache + autovacuum state across
#     tests (each test sees a hot PG, removing cold-cache bias)
#   - Schema stays — Flyway migrations don't re-run
#
# Scope: only the SHARED budget dataset the workload writes. `budget_month` is the root of that tree
# (income / expense / goal / debt and their children carry `ON DELETE CASCADE` back to it), so
# `TRUNCATE budget_month CASCADE` clears the whole month. `fx_rate` is truncated too because the
# workload upserts rates (the conservative default is a code fallback, not a seeded row, so an empty
# table is the correct starting state). Deliberately NOT truncated: `allowed_identity` and
# `salary_preset`, which are seeded at boot by @Startup bootstraps (IdentityBootstrap /
# SalaryPresetBootstrap) — the container stays up across tests, so truncating them would leave them
# empty and break sign-in / preset reads for the next binary. Account/identity tables are left alone
# too; sign-in re-links them idempotently.

set -euo pipefail

PG_DB="${PG_DB:-oppshan}"
PG_SCHEMA="${PG_SCHEMA:-washa}"
COMPOSE_FILE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/docker-compose.yml"

# Resolve THIS pipeline's postgres specifically. A dev box can have many postgres:18 containers
# running (Dev Services / Testcontainers from test runs), so an `ancestor=postgres:18 | head -1`
# filter would truncate an arbitrary one. `docker compose ps` ties to the compose project.
CONTAINER="$(docker compose -f "$COMPOSE_FILE" ps -q postgres 2>/dev/null | head -1)"
[ -n "$CONTAINER" ] || { echo "pgo-reset-db: PGO compose postgres not running (docker compose -f $COMPOSE_FILE ps)" >&2; exit 1; }

echo "===== Truncating budget tables in $CONTAINER ($PG_DB.$PG_SCHEMA) ====="
docker exec "$CONTAINER" psql -U postgres -d "$PG_DB" -v ON_ERROR_STOP=1 -c "
    SET search_path TO $PG_SCHEMA;
    TRUNCATE
        budget_month,
        fx_rate
    CASCADE;
"
echo "pgo-reset-db: done"
