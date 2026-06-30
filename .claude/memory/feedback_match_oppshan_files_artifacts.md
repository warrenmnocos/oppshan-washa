---
name: feedback_match_oppshan_files_artifacts
description: Mirror oppshan-files for NON-code artifacts too — SSM param naming, application.properties section organization, the docs/aws-deployment-* guide set; check the sibling's version of any new artifact type first
type: feedback
---

The "mirror oppshan-files exactly" principle ([[feedback_code_style_and_testing]]) extends past Java/tests to **every artifact type**. Across this session Warren repeatedly redirected washa toward sibling-parity for non-code things.

**Rule:** before creating or organizing a new artifact type, open the matching thing in `/home/warren/Projects/oppshan/files/` and mirror its structure. Demonstrated this session:

- **SSM parameter naming** — flat, literal env-var names under an app prefix: `/oppshan/washa/GOOGLE_CLIENT_ID`, `/oppshan/washa/TOKEN_ENCRYPTION_SECRET`, `/oppshan/washa/QUARKUS_DATASOURCE_{JDBC_URL,USERNAME,PASSWORD}`, `/oppshan/washa/OPPSHAN_WASHA_ALLOWED_IDENTITIES`. The sibling uses `/oppshan/<ENV_VAR>`; the env var equals the `${...}` placeholder in `application.properties` (so dev and prod use the same names) — NOT a hierarchical `/washa/oidc/client-id` scheme and NOT the `QUARKUS_OIDC_*` property names. Warren chose `/oppshan/washa/` (nested under the shared `/oppshan` tree) over `/oppshan-washa/`. Types mirror the sibling: SecureString for secrets + PII, String for the non-secret datasource URL/username.
- **`application.properties` section organization** — a top banner (`# ===...`), then 3-line section dividers (`# ---` / `# Name` / `# ---`), with inline header notes moved onto their own comment line below. Section set: HTTP, Datasource, Hibernate ORM, Flyway, Google OIDC, Allowlist, Coverage, Test profile, Misc.
- **Deployment docs** — the sibling's `docs/` carries `aws-deployment-{manual,cli,terraform,recovery}.md` (+ `operations.md`, `native-image.md`, `diagrams/`). washa mirrors that doc SET, adapted to its serverless stack (the sibling deploys to EC2/Caddy; washa is Lambda/CloudFront/Neon, so washa's are shorter and reference the runnable `infra/` instead of inlining code).

**Why:** the sibling is the reference project Warren maintains and grades against; he reviews washa for parity in everything, not just code.

**How to apply:** for any new config file, doc, or infra artifact, look at oppshan-files first and copy its organization/naming, adapting only what the different architecture genuinely requires.
