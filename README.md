# washa

A small, private web app for the WaSha household: a dashboard at `washa.oppshan.com` that links to
focused apps. The first app is **Budget** — month-by-month household budgeting across currencies,
served at `washa.oppshan.com/budget`.

It's built to run at **zero incremental cost per year** as a single GraalVM-native AWS Lambda
behind CloudFront, with a closed two-person sign-in.

## What it does

The budget app models a two-earner household relocating between the Philippines and Japan:

- **Income** with a real payroll engine: gross salary, ordered pre-tax and post-tax deductions,
  named intermediate variables, and additive tax brackets. Deductions evaluate in order, and
  pre-tax ones lower taxable income for the lines that follow.
- **Expenses**, including a non-removable **tithe** that's always exactly 10% of combined net.
- **Savings goals** with open, fixed-amount, or relative (e.g. six-month-runway) targets.
- **Debts** with amortization, scheduled rate changes, two repricing modes, and prepayment.
- **Multi-currency** throughout: every total reduces to a base currency using conservative,
  per-month FX rates.

The numbers drive real decisions, so the engine is the heart of the app and is tested against the
original single-file prototype (`tokyo_budget_tool.html`) as the behavioral oracle.

## Architecture

```
Browser → Route 53 (washa.oppshan.com)
        → CloudFront  (ACM TLS, edge-caches the Angular bundle)
              └─ Lambda Function URL  (AWS_IAM, OAC-signed)
                    → Quarkus native (arm64) — serves the SPA and /api/**
                          → Neon PostgreSQL (scales to zero)
```

One Quarkus application serves both the compiled Angular SPA and the `/api/**` REST API. The same
native binary is the Lambda. CloudFront caches the hashed static assets at the edge, so the Lambda
is invoked mostly for API calls and cache misses.

**Region:** Lambda, Neon, and Parameter Store live in `ap-northeast-1` (Tokyo); the CloudFront ACM
certificate lives in `us-east-1` because CloudFront requires it there.

### Why it costs nothing

| Service | Always-free allowance | Why we stay inside it |
|---|---|---|
| Lambda | 1M requests + 400k GB-s / month | two users generate a few thousand requests |
| CloudFront | 1 TB egress + 10M requests / month | tiny traffic; edge cache absorbs static assets |
| ACM certificate | free | one cert |
| Neon PostgreSQL | free tier, autosuspends when idle | well within limits |

The only recurring AWS charge is the existing Route 53 hosted zone for `oppshan.com` (about
$0.50/month), which is shared across every app on the domain. Both compute and database scale to
zero when idle, so the steady-state cost is nothing.

## Tech stack

- **Backend:** Quarkus 3.36.3 (Java 25), Hibernate ORM + Jakarta Data, Flyway, RESTEasy + Jackson,
  Quarkus OIDC, compiled to a GraalVM-native arm64 image via `quarkus-amazon-lambda-http`.
- **Frontend:** Angular 22 (standalone, signals, zoneless), TypeScript 6.0, SCSS, no UI framework —
  a hand-rolled design system.
- **Database:** Neon PostgreSQL, Flyway-managed migrations.
- **Auth:** Google OIDC, gated by a two-person allowlist seeded from Parameter Store.
- **CI/CD:** GitHub Actions on free arm64 runners.

## Authentication

Sign-in is Google OIDC, closed to exactly two people. Each person may link **multiple** Google
accounts. A `user_account` owns many `google_account` rows, tethered by the stable OIDC `sub`
(not the email). On first sign-in, a verified email is checked against an allowlist (people →
emails) seeded from Parameter Store; an allowed email links a new Google identity to its person,
and anything else is denied. No identities are hardcoded in source.

## Data model

The budget is one shared household dataset, snapshotted per month. A `budget_month` owns the
income, expenses, goals, and debts for that month; the payroll engine is fully normalized
(`income` → components, deductions, variables, and the brackets under each). Cumulative figures
like goal progress are derived by summing month rows, never stored. Every entity carries
`created_at` and a `last_modified_at` that doubles as the optimistic-lock `@Version`.

## Build, test, run

Requires JDK 25 (GraalVM) and Docker (for test-time PostgreSQL via Dev Services). The Maven build
installs its own Node for the Angular build, so no local Node is needed.

```bash
./mvnw quarkus:dev      # dev mode, hot reload for Java and Angular
./mvnw test             # backend (@QuarkusTest + unit) and frontend (Vitest) together
./mvnw clean install    # full build + the JVM Lambda zip (target/function.zip)
./mvnw -Dnative package # GraalVM-native arm64 build for the Lambda
```

Backend line coverage is ~94% (JaCoCo, excluding generated sources); the frontend runs Vitest.
The salary engine is additionally checked against the prototype's documented regression anchors.

### Profile-guided optimization

`scripts/graalvm-pgo/` holds a PGO pipeline: an instrumented native build is driven through a
representative workload, and the captured profile feeds an optimized build. See
`scripts/graalvm-pgo/README.md`.

## Deployment

CI (`/.github/workflows/ci.yml`) builds and tests both stacks on every push and PR. Deployment
(`cd.yml`) is manual and builds the native arm64 Lambda artifact; the AWS push and CloudFront
invalidation are gated on configuration variables, so the pipeline is exercised without requiring
live infrastructure yet. Secrets come from AWS Parameter Store at runtime and GitHub Secrets in CI
— never from source. There is no SSH and no long-lived AWS key; CI assumes a scoped role via OIDC
federation.

## Repository layout

```
washa/
├── pom.xml                       # Maven + Quarkus + frontend-maven-plugin
├── src/main/java/com/oppshan/washa/   # vertical slices: auth, user, budget, common, config, exception
├── src/main/resources/db/migration/   # Flyway migrations
├── src/main/angular/             # Angular 22 SPA
├── scripts/graalvm-pgo/          # load workload + PGO build pipeline
└── .github/workflows/            # ci.yml, cd.yml
```
