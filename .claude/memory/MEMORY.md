# Project memory

Cross-conversation feedback patterns and project-state snapshots. One concept per file.

- [feedback_code_style_and_testing](feedback_code_style_and_testing.md) — copy oppshan-files style; final var; verbose Jakarta + schema="public"; shouldXxx tests; prefer integration tests
- [feedback_persistence_patterns](feedback_persistence_patterns.md) — always relational (no JSONB); repository pattern (no EntityManager in services); BigDecimal money (no JavaMoney); YearMonth converter; temporal @Version audit
- [feedback_privacy_and_commits](feedback_privacy_and_commits.md) — public repo: never commit real data/emails/names, scrub + grep-gate; no AI attribution; GitHub no-reply git identity
- [project_status_2026-06](project_status_2026-06.md) — full app green on feat/walking-skeleton; CI/CD + PGO + native verified; AWS deploy not yet done
