---
name: project_status_2026-07
description: 2026-07 snapshot — prod healthy on main; a local batch of UX/parity fixes held awaiting Warren's push call; outage root-caused to the auto-bound JVM build clobbering function.zip (pom fix committed, unverified in CD); alias-guard deploy hardening specced but not built
type: project
---

**Prod (washa.oppshan.com):** healthy; serving f53489a (fx-refresh chip pin) deployed 2026-07-05 via
the PGO path. The 2026-07-04/05 outages are root-caused: the quarkus-maven-plugin's auto-bound
default JVM `build` (from `<extensions>true</extensions>`) ran at `package` and overwrote the native
`function.zip` before `save-normal-runner` copied it, so whenever the "normal" candidate won the PGO
throughput race, a bootstrap-less JVM jar shipped and the Lambda died with `Runtime.InvalidEntrypoint`.
Fix committed (`32618ec`, suppresses `default-build` in the native-release-pgo profile) but **its
effect is unverified until a real CD run**; the post-deploy smoke test is the safety net either way.

**Held locally on main, awaiting Warren's explicit push call** (he reviews then says push; deploys are
~40 min PGO builds): the 32618ec pom fix plus a batch of prototype-parity and mobile fixes — carried
months start clean (Discard only after a real edit), remove-× outside .ctrlcol, auto-derived salary
variable names, position-aware formula scope + SalaryEngine publishing deduction vars (backend),
equality-based dirty (deliberate, Warren-directed divergence from the prototype's one-way markDirty),
salary-dialog mobile overflow, fx-slider graduation anchoring. `git log` has details.

**Specced, approved in principle, NOT built:** versioned-alias deploy guard — publish version →
smoke-test `$LATEST`'s own Function URL against real Neon → shift a `live` alias CloudFront points at;
instant rollback via alias re-point. Spec at `docs/superpowers/specs/2026-07-05-versioned-alias-deploy-guard.md`
(gitignored dir). Needs a one-time Warren-run migration (CloudFront origin re-point). He said "check
that later."

**Offered, awaiting approval:** a bootstrap-presence assertion in `scripts/graalvm-pgo/pgo-select-winner.sh`
(belt-and-suspenders for 32618ec; the file historically belongs to the PGO session). Playwright E2E
suite in CI against a local ephemeral stack (decided approach; not started).

**Known open bugs / follow-ups:** (1) a fixture-loaded formula deduction's type `<select>` displays
"percentage" though the formula editor renders — `[value]` races the `@for` options; display-only.
(2) Backward navigation onto an empty month shows it blank; the prototype seeds from the previous
month in both directions (forward-only carry implemented). (3) Verification harness pattern is
documented in `.claude/CLAUDE.md` § verification (stub-harness bullet).
