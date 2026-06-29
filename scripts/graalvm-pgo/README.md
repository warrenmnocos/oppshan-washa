# GraalVM PGO pipeline

Builds a profile-guided-optimized native binary for washa's AWS Lambda with an empirical A/B/C comparison: normal, instrumented, and optimized variants are each built and load-tested, and the higher-throughput of normal-vs-optimized ships as `target/function.zip`.

washa ships as a `quarkus-amazon-lambda-http` handler — a Lambda poll loop, **not** an HTTP server — so the binary can't be booted and curled on a port. The load test runs the real binary under the **AWS Lambda Runtime Interface Emulator (RIE)** and POSTs Function-URL event envelopes at it, exactly the way a Function URL invokes it in prod. That profiles the genuine shipped artifact: the Lambda event adapter, JWT validation, Jackson, and the budget engine.

## Canonical invocation

```bash
bash scripts/graalvm-pgo/pgo-build.sh verify -DskipTests
```

The wrapper invokes `./mvnw -P native-release-pgo` with an `EXIT/INT/TERM` trap that tears down the docker compose stack on every exit path: success, failure, or interruption. Calling `mvn` directly with the profile also works (CI does), but the stack only comes down on the happy path (the `post-integration-test` execution); a halted local build leaves Postgres + Keycloak orphaned, and you have to `docker compose -f scripts/graalvm-pgo/docker-compose.yml down -v` by hand.

Requires Docker and the GraalVM native toolchain. RIE is fetched automatically (or set `RIE_PATH` to a preinstalled binary). **Keep `:8080` free** — RIE binds it for each load test. Budget the better part of an hour: three native builds plus three load tests; native-image needs ~10g heap (see `src/main/java/CLAUDE.md` A.11).

## Maven lifecycle layout

Single reactor; no nested mvn, no shell orchestrator. The `native-release-pgo` profile builds three native binaries (normal, instrumented, optimized), load-tests each, truncates the budget tables between tests so every binary starts from an equivalent empty-budget state, prints a side-by-side comparison, and copies the better of normal-vs-optimized to `target/function.zip`:

| Phase | Action |
|---|---|
| `prepare-package` | `docker compose up` (Postgres 18 + Keycloak 26.5.4); `keycloak-bootstrap.sh` provisions the realm, client (direct-access grants on), and tester users; `quarkus:build` #1 → **normal** native binary (no PGO flags) |
| `package` | save normal runner + `function.zip`; a 60s **warmup** load warms Postgres + Keycloak + the binary, then `pgo-reset-db.sh` restores empty-budget state; `pgo-test-binary.sh normal` runs the measured load; reset DB; `quarkus:build` #2 → **instrumented** native binary (`--pgo-instrument`) |
| `pre-integration-test` | save instrumented runner; `pgo-test-binary.sh instrumented` runs the load and captures `default.iprof` (the binary is SIGTERM-stopped so GraalVM flushes the profile); reset DB; `quarkus:build` #3 → **optimized** native binary (`--pgo=target/pgo-run/default.iprof`) |
| `integration-test` | save optimized runner + `function.zip`; `pgo-test-binary.sh optimized` runs the load; `pgo-compare.sh` prints the comparison; `pgo-select-winner.sh` copies the higher-throughput binary's `function.zip` to `target/function.zip` and deletes the throwaway instrumented runner |
| `post-integration-test` | `docker compose down -v` |

`exec-maven-plugin` is declared before `quarkus-maven-plugin` in the profile so within any shared phase the exec executions (save + test) run before the next `quarkus:build`, giving the interleaved build → save → test sequence in a single reactor. Each save step copies the just-built native `function.zip` before the next build overwrites it.

## Load workload per binary (via RIE)

`pgo-test-binary.sh` launches the binary under `aws-lambda-rie` (RIE provides the Lambda Runtime API the binary polls, and exposes an invoke endpoint on `:8080`), then `parallel-workload.sh` → `workload.sh` drives it: a single sustained stream of Function-URL invocations over the CPU-hot budget paths — `POST /api/budget/compute` (the salary→net engine, formula evaluator, additive brackets, debt amortization), `GET /api/me` (JWT validation on every authenticated call), FX lookups, and a month read/write.

The stream is **single** on purpose: RIE emulates one Lambda execution environment and serializes invocations, so concurrent streams collide on it and most invocations fail. One stream is both reliable and the faithful prod model — a Lambda instance serves one request at a time. (The multi-worker plumbing in `parallel-workload.sh` is kept for a possible future multi-RIE-instance setup, but `WORKER_COUNT` stays 1 for a single RIE.)

Before the first measured test, a **60-second warmup** runs the same load against the normal binary to warm Postgres, Keycloak's token endpoint, and the binary itself; the DB is reset between the warmup and the measured normal run so all three binaries are measured from the same warm-infra, empty-budget state.

## Auth and the `%pgo` profile

washa's prod auth is Google OIDC on the default tenant (`provider=google`). The `%pgo` Quarkus profile (in `application.properties`) unsets that preset, repoints the default tenant at the throwaway docker-compose Keycloak (`auth-server-url` + client secret injected by `pgo-test-binary.sh`), sets it to **`hybrid`** so it accepts bearer tokens, and widens `washa.allowed-identities` to the `washa-pgo-*@example.com` testers. The workload mints a bearer token via the Keycloak password grant and attaches it to each event — far simpler than threading the OIDC code flow through Lambda event envelopes, and it still profiles JWT validation on every call. Everything is localhost-only with sample secrets and identities; none of it reaches prod.

## Files in this directory

| File | Role |
|---|---|
| `pgo-build.sh` | Canonical wrapper with EXIT/INT/TERM compose teardown trap (local runs) |
| `pgo-test-binary.sh` | Runs one binary under RIE, drives the workload, SIGTERM-stops it (flushing `default.iprof`), captures metrics |
| `parallel-workload.sh` | Runs the workload stream(s); `WORKER_COUNT=1` for a single RIE |
| `workload.sh` | Mints a bearer token, then POSTs Function-URL event invocations over the budget hot paths |
| `pgo-reset-db.sh` | Truncates `budget_month` + `fx_rate` between test runs (leaves boot-seeded tables alone) |
| `pgo-compare.sh` | Prints the side-by-side comparison table |
| `pgo-select-winner.sh` | Copies the higher-throughput binary's `function.zip` to the canonical artifact path |
| `keycloak-bootstrap.sh` | Provisions the realm, OIDC client (direct-access grants), and tester users |
| `docker-compose.yml` | Postgres 18 + Keycloak 26.5.4 |
| `pg-init/01-extensions.sql` | Init script mounted into the Postgres container (`pg_stat_statements`) |
