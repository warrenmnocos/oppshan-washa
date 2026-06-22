# washa — Database Migrations

Flyway-managed. Migrations in `migration/postgresql/`. Tests use real PostgreSQL via
Quarkus Dev Services (Testcontainers; Docker required); prod is Neon PostgreSQL.

- **File naming:** `V<n>__<snake_case_description>.sql`. Version `<n>` is the next integer; description is lowercase with underscores. The latest applied version + per-migration summary lives in the README "Migrations" table — that's the historical record, not git log.
- **Application config** (`src/main/resources/application.properties`): `quarkus.flyway.migrate-at-start=true` runs migrations on startup; `quarkus.hibernate-orm.database.generation=validate` halts on schema drift. Test profile uses `drop-and-create` via Dev Services.
- **Never edit an applied migration.** Add `V{n+1}__description.sql` — schema changes increment the version.
- **Bundle related fixes into a still-staged migration.** If `V<n>` is in the repo but not yet recorded in `flyway_schema_history` on any environment Flyway has run against, follow-up fixes can be folded into the same file (rename if scope shifts). Once Flyway records it anywhere, treat the file as immutable and start `V<n+1>`.
- **Single dialect.** Write standard PostgreSQL SQL.
- Hibernate validates schema on startup (`halt-on-error=true`). Tests use `drop-and-create`.
- **Drop+recreate UCs explicitly when their backing column changes.** PostgreSQL silently cascades same-table constraint drops with their column drops (only external dependencies like FKs need explicit `CASCADE`). So if you drop a column that a `UNIQUE` constraint covers, the constraint vanishes silently. After this kind of change, grep `pg_constraint` against the entity's expected UC set before considering the migration done.
- **Non-destructive prod data fixes.** When prod data violates a new constraint, prefer rename / disambiguate over delete. Propose deterministic, conflict-checked suffix schemes; verify no collision before renaming. There is no prod shell (washa runs as a Lambda) — apply prod data fixes through a Flyway migration or a reviewed one-off, not an ad-hoc session.
- **README schema sync.** When a migration adds/changes a column, constraint, or index, update the README "Migrations" table in the same commit; bump `rowspan` on the affected "Domain Model" and "View types" rows. The ERD lists column names but not NN markers — README tables are authoritative on nullability.
- **`FlywayMigrationTest`** locks migration parity: a JUnit test that brings up the schema via Flyway against a Testcontainers Postgres and asserts no Hibernate `validate`-mode complaints. Run via `./mvnw test -Dtest=FlywayMigrationTest`. If it fails after editing a migration, you've drifted the entity model from the schema (or vice versa) — fix before merging.
