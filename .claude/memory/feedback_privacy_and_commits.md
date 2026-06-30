---
name: feedback_privacy_and_commits
description: Public-repo privacy rules and commit conventions — never commit real financial data/emails/names, scrub to examples, no AI attribution, use the GitHub no-reply git identity
type: feedback
---

`warrenmnocos/oppshan-washa` is a **public** GitHub repo (public is required for free arm64 CI).
Privacy is a hard, repeatedly-reinforced requirement.

**Rule:**
- **Never commit real personal/financial data.** The budget mockup (`tokyo_budget_tool.html`),
  the sample export (`tokyo-budget-*.json`), the handover (`HANDOVER_budget_app.md`), and the
  `docs/superpowers/` planning tree are **gitignored, local-only**. In any committed file (code,
  docs, tests, memory) use sanitized examples — round example salaries, `alice@example.com` /
  `bob@example.com`, generic names. Keep any real-figure regression test as a **gitignored** local
  fixture (e.g. the salary-engine oracle test), never committed.
- **Editing a gitignored secrets file (`.env`):** to rename or restructure a key, use an anchored
  `sed -i 's/^OLD_KEY=/NEW_KEY=/' .env` rather than Read + Edit — it changes only the key name and
  never pulls the secret value into the working context (the dedicated Edit tool requires reading the
  file first, which would).
- **Before committing/pushing, grep the staged content for sensitive tokens** (real email
  addresses, the company-domain email, the real salary/mortgage figures) and confirm zero hits.
  This has bitten before — the company email once reached the public `main` and the repo had to be
  rebuilt and recreated.
- **Commit messages: no Claude/AI attribution.** Plain past-tense `oppshan-files` style. No
  `Co-Authored-By` trailer, no "generated with" line.
- **Git identity for this repo is the GitHub no-reply email** (set repo-local in `.git/config`),
  **never the machine's global/company email.** If `.git` is re-initialized, re-set it immediately.

**Why:** the repo is world-readable; income/debt/identities must not be exposed. History rewrites
are costly and incomplete (dangling commits persist by SHA until GC), so prevention beats cleanup.

**How to apply:** treat every commit to this repo as a publication. Scrub first, grep-gate, then
commit. Real data lives only in Neon (private) and Parameter Store at runtime.
