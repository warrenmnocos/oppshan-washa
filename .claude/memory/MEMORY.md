# Project memory

Cross-conversation feedback patterns and project-state snapshots. One concept per file.

- [feedback_code_style_and_testing](feedback_code_style_and_testing.md) — copy oppshan-files style; final var; verbose Jakarta + schema="washa"; shouldXxx tests; prefer integration tests
- [feedback_persistence_patterns](feedback_persistence_patterns.md) — always relational (no JSONB); repository pattern (no EntityManager in services); BigDecimal money (no JavaMoney); YearMonth converter; temporal @Version audit
- [feedback_privacy_and_commits](feedback_privacy_and_commits.md) — public repo: never commit real data/emails/names, scrub + grep-gate; no AI attribution; GitHub no-reply git identity
- [feedback_portal_and_design](feedback_portal_and_design.md) — washa is a multi-app portal (budget is one app): keep shared pages domain-agnostic; shares files' design language in its own amber palette, token-driven + app shell; rem not px
- [feedback_docs_vs_actual](feedback_docs_vs_actual.md) — CLAUDE.md docs describe oppshan-files patterns washa never built (event bus, UserSessionManager, SPA filter, ngx-translate); verify against code; washa uses signals + a signal store, signal-based dialogs
- [project_status_2026-06](project_status_2026-06.md) — green on feat/walking-skeleton; DB=oppshan/schema=washa (V4); auth/login + amber reskin + app shell done; budget parity in progress (3 dialogs + compute done); AWS deploy not yet done
