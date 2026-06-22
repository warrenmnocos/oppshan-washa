---
name: project_status_2026-06
description: Build status as of 2026-06-22 — full app implemented and green on feat/walking-skeleton; CI/CD + PGO + native verified; AWS deployment not yet done
type: project
---

As of 2026-06-22, washa is a complete, tested full-stack app on branch **`feat/walking-skeleton`**
(not yet merged to `main`).

**Done and green** (`./mvnw clean install` succeeds):
- **Backend** (`com.oppshan.washa`): allowlist-gated Google OIDC (`/api/me`, `/sso/*`), identity
  model (UserAccount ↔ IdpAccount/GoogleAccount, AllowedIdentity), fully-relational budget schema
  (V1 identity, V2 budget, V3 goal index), the budget engine (formula evaluator, salary→net engine,
  currency/tithe, debt amortization), `BudgetService`, and the `/api/budget` REST API (month CRUD,
  compute, fx). Virtual-thread Undertow extension. ~95% line coverage (JaCoCo, generated excluded).
- **Frontend** (Angular 22, standalone/signals/zoneless, hand-rolled SCSS porting the mockup's
  Tokyo-paper design): dashboard + full budget page (income/expenses/goals/debts/FX, month nav,
  JSON export/import, SVG donut chart) via a signal store calling `/api/budget`. ~89% line coverage
  (Vitest). Responsive. NOTE: the frontend `CLAUDE.md` still describes the oppshan-files event-bus/
  CQRS + class-transformer architecture, which washa does NOT use — it uses a signal store and no
  class-transformer; that doc is unreconciled.
- **Build/deploy:** Quarkus 3.36.3 / Angular 22.0.2 / TS 6.0.3 / Node v26.3.1 (latest supported).
  CI (`ci.yml`) builds+tests on arm64; CD (`cd.yml`) is manual and gated on AWS vars (no infra yet).
  PGO pipeline in `scripts/graalvm-pgo/`. **Native arm64 build verified** (needs ~10g heap).
- **Correctness:** the engine reproduces the mockup's JP payroll math exactly (gitignored oracle
  test against the handover regression anchors).

**Not done:** AWS infrastructure (Plan 1 Tasks 13–18 — Neon, Parameter Store, Lambda, Function URL,
OAC, CloudFront, ACM, Route 53 records, GitHub OIDC role). The CAA record on `oppshan.com` must be
widened to allow `amazon.com` before ACM issuance. Spec + plans live in (gitignored)
`docs/superpowers/`.
