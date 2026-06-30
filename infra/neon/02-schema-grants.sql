-- washa — schema + default privileges (block 2 of 2). Run as washa_admin on the DIRECT endpoint:
--   psql "postgresql://washa_admin:$ADMIN_PW@<direct host>/oppshan?sslmode=require" -f infra/neon/02-schema-grants.sql
--
-- washa_admin owns the schema and sets default privileges for its OWN future objects, so this MUST run as
-- washa_admin: neondb_owner can't act on its behalf without SET ROLE membership it doesn't hold. After this,
-- every table Flyway creates (as washa_admin) is automatically DML-accessible to washa_user.
CREATE SCHEMA IF NOT EXISTS washa;
GRANT USAGE ON SCHEMA washa TO washa_user;

ALTER DEFAULT PRIVILEGES IN SCHEMA washa GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES    TO washa_user;
ALTER DEFAULT PRIVILEGES IN SCHEMA washa GRANT USAGE, SELECT                 ON SEQUENCES TO washa_user;
