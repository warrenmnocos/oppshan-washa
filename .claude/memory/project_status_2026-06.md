---
name: project_status_2026-06
description: Build status as of 2026-06-23 — app green on feat/walking-skeleton; schema flipped to DB=oppshan/schema=washa; auth/login + amber reskin + app shell done; budget feature-parity vs the prototype now ~high-90s% (no P1 gaps); AWS deploy IaC built + validated but not yet applied (see the 2026-06-29 update)
type: project
---

As of 2026-06-23, washa is a working, tested full-stack app on branch **`feat/walking-skeleton`**
(not yet merged to `main`). Backend tests + frontend (Vitest, 63 specs) green; native arm64 build
verified earlier.

**Settled this session:**
- **DB/schema layout: database `oppshan` (org-level container), schema `washa` (this app).** Future
  oppshan apps each take their own schema in the one `oppshan` database (one Neon DB, schema-per-app).
  `@Table(schema="washa")` ×16, Hibernate `default-schema=washa` + `validate` + `halt-on-error` +
  `jdbc.timezone=UTC`, Flyway `schemas=washa`. **V4** alters `year_month` CHAR(7)→VARCHAR(7) to match
  the entity mapping. (Local dev: `oppshan` DB + `washa_app` role provisioned into the system Postgres;
  secrets in a gitignored repo-root `.env` that Quarkus auto-loads.)
- **Auth/login rework.** `/api/me` is no longer `@Authenticated` — 401 signed-out / 200 signed-in /
  403 not-allowlisted — so the SPA shows a **public `/sso/sign-in` page** (guarded by `guestGuard`)
  instead of bouncing to Google; the backend OIDC trigger is `/sso/sign-in/oidc/google`. Built the
  previously-documented-but-missing `UserSessionManager`/`OidcUserSessionManager` (`@RequestScoped`,
  `Instance<OidcSession>`, no SessionScoped cache) and `FrontendRoutesFilter` (SPA fallback). Sign-out
  is a **local** logout (Google has no `end_session_endpoint`); OIDC requests `access_type=offline`.
  `BusinessExceptionMapper` now emits the i18n **key** (`getKey()`, `@JsonValue`) not the enum name;
  added `signInFailed` across Java+TS+en.json. See [[feedback_docs_vs_actual]].
- **Amber reskin + app shell.** washa now wears the oppshan design language in its own amber palette,
  token-driven; sign-in/dashboard/shared header+footer rebuilt; an `AppShell` frames signed-in pages
  (fixed header/footer, scrolling content). **ngx-translate** (core+http-loader 18) wired to
  `/i18n/en.json`. `styles.scss` converted px→rem and prettified. See [[feedback_portal_and_design]].

**Budget feature-parity vs `tokyo_budget_tool.html`** (the behavioral source of truth; a full
63-feature audit exists):
- **Done:** `compute()` now sums money-out = expenses(+tithe line) + all goals + debt(amort+prepay)
  so free is the true remainder; `ComputedView` gained otherExpenses/debt/savingsGoals/nonSavingsGoals/
  savingsRate; the chart shows the baseline's six segments; an "Invested & saved" metric. **Three edit
  dialogs** (salary: components/variables/deductions; goal: target/savings/withdrawals; debt: rate
  steps/reprice/prepayment) — the payroll/goal/debt engines are finally reachable from the UI.
- **Remaining:** currency management (add/remove, reorder base, rate slider, market fetch — pure
  frontend); goal-progress card + debt prepay-vs-not projection (need a `compute()`/endpoint extension);
  time-based goal targets + close-goal + income presets + the salary tax-**bracket** sub-editor (need a
  **V5** migration for new columns + a preset store); polish (skeletons, save/dirty bar, metric
  auto-size, print CSS). Dark mode + responsiveness already done via the reskin.

**Not done (as of 2026-06-23):** AWS infrastructure. Spec/plans in (gitignored) `docs/superpowers/`.

## Update — 2026-06-29

- **AWS deployment IaC is now BUILT and validated** (not yet applied). Two interchangeable provisioners under `infra/`: `infra/terraform/` (declarative) and `infra/cli/` (`aws` CLI scripts + the shared `seed-secrets.sh`/`set-lambda-env.sh`). They stand up the `washa` Lambda + IAM-auth Function URL + OAC + CloudFront (3 behaviors + the static `X-Forwarded-Host`/`-Proto` origin headers OIDC needs) + us-east-1 ACM + Route 53 alias + GitHub OIDC deploy role + 7 SSM slots. Verified against the live account (`oppshan-admin`, acct 205839628610): `terraform plan` = **22 to add, 0 change, 0 destroy**. NOT applied — no live infra; runbook in `infra/README.md` + `docs/aws-deployment-*.md`.
- **Secret-free Terraform state:** each SSM param is a placeholder slot with `ignore_changes=[value]`, the Lambda env is `ignore_changes`, and real values are seeded out-of-band by the CLI scripts → no secret in state/tfvars. The GitHub OIDC provider already exists in the account (sibling app), so the TF toggle defaults to referencing it.
- **Neon stays external** (consumed via SSM, not provisioned) — deliberately, to keep the DB password out of TF state. **The Lambda runs in `ap-southeast-1` (Singapore), co-located with Neon** (decided 2026-06-29): the per-request hot path is DB-chatty (`getMonth`, plus `/compute` which is an N+1 over goals), so co-locating pays the Tokyo↔SG round-trip once (user→Lambda) instead of once per query, while CloudFront still serves static from the Tokyo edge. Both Lambda + Neon scale to zero idle and auto-resume on request → ~$0 for two users.
- **SSM/env naming + `application.properties` now mirror oppshan-files** (`/oppshan/washa/<ENV_VAR>`, banner + 3-line section dividers). CLAUDE.md C.4 + the README/layout updated. See [[feedback_match_oppshan_files_artifacts]].
- **Budget parity vs the prototype is ~high-90s% with no P1 gaps** (fresh gap analysis: only polish items). Fixed the donut chart's oversized center % (moved its SVG into the prototype's 200-unit viewBox). washa renders a donut where the prototype is a full pie — a deliberate, accepted divergence.
- **A parallel Claude session owns the PGO pipeline** and committed it this period. See [[feedback_parallel_session_commits]] — its surfaces are off-limits to the deployment/UI session.
- Branch `feat/walking-skeleton` is ~95+ commits ahead of `origin`; `main` is still essentially empty. Not merged or pushed (Warren pushes).

## Update — 2026-06-30

- **Region move + `oppshan-washa` namespacing shipped.** Implemented the SG co-location (Lambda → `ap-southeast-1`, with Neon) and renamed every standalone AWS resource `washa` → `oppshan-washa` plus the allowlist env var → `OPPSHAN_WASHA_ALLOWED_IDENTITIES`, keeping bare `washa` where its container is already `oppshan`. Swept `infra/`, `docs/aws-deployment-*`, `cd.yml`, root `CLAUDE.md`, the two `config/*.java` comments, the gitignored `.env` + the three `docs/superpowers/` design docs + the CI/CD diagram; whole-repo grep is clean. The full rule lives in [[feedback_oppshan_washa_namespacing]]; Warren's drive for exhaustive scope across gitignored/historical files is in [[feedback_apply_changes_exhaustively]].
- **Perf follow-up (non-obvious):** `BudgetService.compute` calls `goalRepository.sumContributionsBefore(...)` **once per goal** (an N+1 inside the goal loop) plus the debt prepay-year query — this DB-chattiness on the per-request hot path is what drove the SG co-location. Batching it into one query grouped by `(label, currency)` would cut `/compute` to ~2 queries; in-region now so not urgent, but worth doing.
- **Dependencies bumped (`/upgrade-deps`, commit `7b025ca`):** Quarkus **3.36.3 → 3.37.0** (Hibernate ORM 7.4 — migration guide checked, nothing applicable to this PostgreSQL-18 / no-`@Any` / no-Reactive stack), Node `v26.4.0`, `@angular/*` + cli `22.0.4`, `frontend-maven-plugin` `2.0.1`, vitest `4.1.9`. Held (already latest compatible): guava, jakarta.data-api, the Maven plugins, ngx-translate, rxjs, tslib, jsdom, typescript (6.0.3, Angular 22's `>=6.0 <6.1` ceiling). Verified once the tree quiesced: JVM `clean install` **107/107** + Vitest + Lambda jar, and a local **native-release** `BUILD SUCCESS` (local binary is x86-64; CI builds the arm64 Lambda).
- **`UT000127` log-noise fixed (commit `f77e1d8`):** the FX-slider `switchMap` cancels each in-flight `/api/budget/compute` mid-response, so Undertow logged "Response has already been sent" at ERROR. Benign client-cancellation (compute is side-effect-free; effectively a dev-mode artifact, the buffered Lambda HTTP model doesn't hit it). `ClientDisconnectLogFilter` (a `@LoggingFilter`) drops it narrowly: only `io.undertow.request.io` records whose rendered message names `/api/budget/compute` AND whose cause chain carries a disconnect marker (`UT000127`/broken pipe/connection reset), so other endpoints and genuine errors still log, on every profile.
- **Deps verification depended on the other session's test fixes:** reused Dev Services containers had stale `budget_month`/`fx_rate` rows that failed the suite (looked like a Quarkus-3.37 regression but wasn't); their `devservices.reuse=false` (`7131c98`) + order-independent fx-rate/salary-preset test fixes (`0a4ba71`, `8b8edf0`) green'd it. Backend `CLAUDE.md` **A.10** still calls Testcontainers reuse ON/accumulating — now stale given `devservices.reuse=false`, a sync follow-up for the test-infra session.
