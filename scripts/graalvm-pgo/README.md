# GraalVM PGO — profile-guided native build

Profile-guided optimization for washa's native arm64 AWS Lambda. A first native build is
**instrumented**, driven through a representative workload to capture a profile, then a second
**optimized** native build consumes that profile — typically improving cold-start and steady-state
throughput on the hot paths (the salary→net engine, formula evaluator, debt amortization).

## Scripts

| Script | Role |
|---|---|
| `pgo-build.sh` | Orchestrates the full instrument → load → optimize cycle; emits `target/function.zip`. |
| `workload.sh` | Drives a running instance through the CPU-hot budget paths (`/api/budget/compute`, FX, SPA). |

## Run

```bash
bash scripts/graalvm-pgo/pgo-build.sh
```

Requires the GraalVM native toolchain and Docker (Dev Services PostgreSQL for the running
instance). The optimized artifact lands at `target/function.zip`.

## Workload auth

`workload.sh` exercises the authenticated budget endpoints when `WASHA_LOAD_TOKEN` is set to a
bearer token (e.g. minted by the OIDC test server); without it, it still drives SPA static serving
and the health endpoint. The payload uses **sanitized example figures only** — no real identities
or amounts.

## CI

The `Deploy` workflow's optional `pgo` input runs this pipeline on the arm64 runner before
deploying. The plain native build (`./mvnw -Dnative package`) remains the default; PGO is opt-in.
