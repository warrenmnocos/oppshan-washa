-- Mounted into the postgres:18 container at /docker-entrypoint-initdb.d/01-extensions.sql by
-- docker-compose.yml. The official postgres image runs every *.sql file in that directory exactly
-- once, on first container start, against POSTGRES_DB (oppshan).
--
-- pg_stat_statements is loaded via shared_preload_libraries on the postgres command line;
-- CREATE EXTENSION exposes the SQL-level view that records per-query stats. Idempotent: the
-- IF NOT EXISTS guard means re-running this against an existing DB (e.g. if the volume gets
-- reused across compose runs) is harmless.

CREATE EXTENSION IF NOT EXISTS pg_stat_statements;
