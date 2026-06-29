# Project memory

Cross-conversation feedback patterns and project-state snapshots. One concept per file.

- [feedback_code_style_and_testing](feedback_code_style_and_testing.md) — copy oppshan-files style; final var; verbose Jakarta + schema="washa"; shouldXxx tests; prefer integration tests
- [feedback_persistence_patterns](feedback_persistence_patterns.md) — always relational (no JSONB); repository pattern (no EntityManager in services); BigDecimal money (no JavaMoney); YearMonth converter; temporal @Version audit
- [feedback_privacy_and_commits](feedback_privacy_and_commits.md) — public repo: never commit real data/emails/names, scrub + grep-gate; no AI attribution; GitHub no-reply git identity
- [feedback_portal_and_design](feedback_portal_and_design.md) — washa is a multi-app portal (budget is one app): keep shared pages domain-agnostic; shares files' design language in its own amber palette, token-driven + app shell; rem not px
- [feedback_docs_vs_actual](feedback_docs_vs_actual.md) — CLAUDE.md docs describe oppshan-files patterns washa never built (event bus, UserSessionManager, SPA filter, ngx-translate); verify against code; washa uses signals + a signal store, signal-based dialogs
- [feedback_proactive_tech_currency](feedback_proactive_tech_currency.md) — proactively surface modern features across ALL libraries, frontend and backend (Angular AND Quarkus/Hibernate/Flyway/…); verify against live docs
- [feedback_match_oppshan_files_artifacts](feedback_match_oppshan_files_artifacts.md) — mirror oppshan-files for non-code artifacts too: SSM param naming (`/oppshan/washa/<ENV_VAR>`), application.properties section organization, the docs/aws-deployment-* guide set
- [feedback_parallel_session_commits](feedback_parallel_session_commits.md) — two Claude sessions share this repo: commit explicit paths only (`git commit -- <paths>`); stay off the other session's PGO surfaces
- [project_status_2026-06](project_status_2026-06.md) — feat/walking-skeleton; DB=oppshan/schema=washa; auth + amber reskin done; budget parity ~high-90s% (no P1); AWS deploy IaC built+validated (not applied); parallel PGO session active
