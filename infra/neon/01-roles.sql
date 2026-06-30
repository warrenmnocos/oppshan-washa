-- washa — Neon role creation (block 1 of 2). Run as neondb_owner on the DIRECT endpoint:
--   psql "<neondb_owner direct url>" -v admin_pw="$ADMIN_PW" -v user_pw="$USER_PW" -f infra/neon/01-roles.sql
-- Then run 02-schema-grants.sql as washa_admin. See docs/aws-deployment-*.md Phase 0.
--
-- Two least-privilege roles, each on its own endpoint:
--   washa_admin — migrations (Flyway / DDL), DIRECT endpoint (so Flyway's advisory lock serializes cold starts).
--   washa_user  — app runtime (DML-only),    POOLED endpoint.
-- Neither is a member of neon_superuser, and neither has CREATEDB / CREATEROLE / BYPASSRLS, so a leaked
-- credential or a SQL-injection bug stays contained to its lane.
CREATE ROLE washa_admin LOGIN PASSWORD :'admin_pw' NOCREATEDB NOCREATEROLE NOBYPASSRLS;
CREATE ROLE washa_user  LOGIN PASSWORD :'user_pw'  NOCREATEDB NOCREATEROLE NOBYPASSRLS;

GRANT CONNECT, CREATE ON DATABASE oppshan TO washa_admin;  -- connect + create/own its schema
GRANT CONNECT            ON DATABASE oppshan TO washa_user;  -- connect only; DML arrives via 02's default privileges
