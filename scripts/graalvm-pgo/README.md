# GraalVM PGO pipeline

Builds a profile-guided-optimized native binary for washa's arm64 AWS Lambda with an empirical A/B/C comparison: normal, instrumented, and optimized variants are each built and load-tested against a 10-worker concurrent workload, and the higher-throughput of normal-vs-optimized ships as `target/function.zip`.

## Canonical invocation

```bash
bash scripts/graalvm-pgo/pgo-build.sh verify -DskipTests
```

The wrapper invokes `./mvnw -P native-release-pgo` with an `EXIT/INT/TERM` trap that tears down the docker compose stack on every exit path: success, failure, or interruption. Calling `mvn` directly with the profile also works (CI does), but the docker compose stack only comes down on the happy path (via the `post-integration-test` execution); a halted local build leaves Postgres + Keycloak orphaned, and you have to `docker compose -f scripts/graalvm-pgo/docker-compose.yml down -v` by hand.

Requires Docker running and the GraalVM native toolchain. Total wall-clock is dominated by three native builds plus three load tests, so budget the better part of an hour on a workstation; native-image needs ~10g heap (see `src/main/java/CLAUDE.md` A.11).

## Maven lifecycle layout

Single reactor; no nested mvn, no shell orchestrator. The `native-release-pgo` profile builds three native binaries (normal, instrumented, optimized), load-tests each, truncates the budget tables between tests so every binary starts from an equivalent empty-budget state, prints a side-by-side comparison, and copies the better of normal-vs-optimized to `target/function.zip`:

| Phase | Action |
|---|---|
| `prepare-package` | `docker compose up` (Postgres 18 + Keycloak 26.5.4); `keycloak-bootstrap.sh` provisions 10 tester users + the OIDC client; `quarkus:build` #1 → **normal** native binary (no PGO flags) |
| `package` | save normal runner + `function.zip`; a 60s **warmup** load warms the shared Postgres + Keycloak, then `pgo-reset-db.sh` restores empty-budget state; `pgo-test-binary.sh normal` runs the measured load workload; reset DB; `quarkus:build` #2 → **instrumented** native binary (`--pgo-instrument`) |
| `pre-integration-test` | save instrumented runner; `pgo-test-binary.sh instrumented` runs the workload (captures `default.iprof`); reset DB; `quarkus:build` #3 → **optimized** native binary (`--pgo=target/pgo-run/default.iprof`) |
| `integration-test` | save optimized runner + `function.zip`; `pgo-test-binary.sh optimized` runs the workload; `pgo-compare.sh` prints the comparison; `pgo-select-winner.sh` copies the higher-throughput binary's `function.zip` to `target/function.zip` and deletes the throwaway instrumented runner |
| `post-integration-test` | `docker compose down -v` |

The relaxed gate in `parallel-workload.sh` accepts any run with ≥1 successful worker, so a transient flake doesn't abort it. `exec-maven-plugin` is declared before `quarkus-maven-plugin` in the profile so within any shared phase the exec executions (save + test) run before the next `quarkus:build`, giving the interleaved build → save → test → build → save → test → build → save → test sequence in a single reactor. Each save step copies the just-built native `function.zip` before the next build overwrites it.

## Load workload per binary

`pgo-test-binary.sh` → `parallel-workload.sh` → `workload.sh`: 10 concurrent OIDC sessions, each signing in through the real Keycloak code flow and then looping the CPU-hot budget paths — `POST /api/budget/compute` (the salary→net engine, formula evaluator, additive brackets, debt amortization, all stateless), the per-request encrypted-session decrypt every authenticated `/api/**` call pays, FX lookups, and a month read/write keyed to that worker's own `year_month` (so concurrent writes never collide on `budget_month`'s unique key). The binary launches under `QUARKUS_PROFILE=pgo` with `-Djdk.virtualThreadScheduler.parallelism=$(nproc)`.

washa runs as a Lambda — in prod each concurrent request gets its own execution environment, so one binary never serves 10 at once. The concurrency here is about PGO sample volume and exercising shared/thread-safe code paths, not modelling prod load shape.

Before the first measured test, a **60-second warmup** runs the same workload against the normal binary purely to warm the shared Postgres page cache and Keycloak token endpoints. The DB is reset between the warmup and the measured normal run. Without it the normal binary — tested first — would absorb the cold-infra penalty alone while instrumented and optimized inherit warm containers; the warmup makes all three comparable.

## Auth and the `%pgo` profile

washa's prod auth is Google OIDC on the default tenant (`provider=google`). The `%pgo` Quarkus profile (in `application.properties`) unsets that preset and repoints the default tenant at the throwaway docker-compose Keycloak (`auth-server-url` + client secret injected by `pgo-test-binary.sh`), widens `washa.allowed-identities` to the 10 `washa-pgo-*@example.com` testers, pins `prompt=login` so the workload only ever posts Keycloak's single login form, and bumps the JDBC pool so 10 workers aren't serialised behind the prod-sized 2-connection pool. Everything is localhost-only with sample secrets and identities; none of it reaches prod.

## Files in this directory

| File | Role |
|---|---|
| `pgo-build.sh` | Canonical wrapper with EXIT/INT/TERM compose teardown trap (local runs) |
| `pgo-test-binary.sh` | Boots one binary under `QUARKUS_PROFILE=pgo`, drives `parallel-workload.sh` against it, captures metrics |
| `parallel-workload.sh` | Spawns N concurrent `workload.sh` instances, each a distinct Keycloak user + month key |
| `workload.sh` | Per-worker OIDC sign-in + budget-engine loop against the local binary |
| `pgo-reset-db.sh` | Truncates `budget_month` + `fx_rate` between test runs (leaves boot-seeded tables alone) |
| `pgo-compare.sh` | Prints the side-by-side comparison table |
| `pgo-select-winner.sh` | Copies the higher-throughput binary's `function.zip` to the canonical artifact path |
| `keycloak-bootstrap.sh` | Provisions the realm, OIDC client, and 10 tester users |
| `docker-compose.yml` | Postgres 18 + Keycloak 26.5.4 |
| `pg-init/01-extensions.sql` | Init script mounted into the Postgres container (`pg_stat_statements`) |
