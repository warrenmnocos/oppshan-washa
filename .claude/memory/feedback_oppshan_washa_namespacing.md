---
name: feedback_oppshan_washa_namespacing
description: Naming rule — standalone AWS resources are oppshan-washa-prefixed and paths are /oppshan/washa/, but bare washa stays wherever its enclosing group/project/namespace is already oppshan (DB/schema/role/subdomain/package/SSM path); the flat Lambda allowlist env var self-namespaces as OPPSHAN_WASHA_ALLOWED_IDENTITIES
type: feedback
---

Deployment identifiers follow a deliberate **"never `washa` without `oppshan`"** rule, with one
exception: `washa` is fine when its container already carries `oppshan`. Settled 2026-06-30; extends
the `/oppshan/washa/` SSM-path choice in [[feedback_match_oppshan_files_artifacts]]. Authoritative
values live in root `CLAUDE.md` C.4.

**Rule:**
- **Standalone AWS resources → `oppshan-washa-*`** (they have no enclosing oppshan namespace; AWS
  account namespaces are flat): the Lambda function, its exec + GitHub-deploy roles, the OAC, the log
  group, the CloudFront origin id, the Terraform state bucket + lock table, the CD artifact. Both
  provisioners derive every one from a **single knob** — `FUNCTION_NAME` in `infra/cli/lib.sh`,
  `var.function_name` in `infra/terraform/` — and `cd.yml` targets the literal name, so move those in
  lockstep.
- **Path-namespaced things → `/oppshan/washa/…`** (SSM parameters).
- **Bare `washa` stays where the enclosing group/project/namespace is already `oppshan`:** the Neon
  **`oppshan` database** + its **`washa` schema** and **`washa` login role** (the role lives in the
  `oppshan` Neon project), the **`washa.oppshan.com`** subdomain, the **`com.oppshan.washa`** Java
  package, the **`washa.*`** Quarkus config root, the **`/oppshan/washa`** SSM path prefix.
- **The Lambda's flat allowlist env var self-namespaces → `OPPSHAN_WASHA_ALLOWED_IDENTITIES`**
  (Warren's explicit call): a Lambda's env vars are a flat list with no oppshan container, so the var
  carries the prefix itself. `application.properties` binds it via
  `washa.allowed-identities=${OPPSHAN_WASHA_ALLOWED_IDENTITIES:[]}` — the property name is unchanged
  (it is under the `washa.*` config root). The SSM slot matches: `/oppshan/washa/OPPSHAN_WASHA_ALLOWED_IDENTITIES`.
- **Internal-only identifiers keep `washa`:** Terraform resource labels
  (`resource "aws_lambda_function" "washa"`), the `[washa]` shell log prefix, and diagram/doc titles
  (the app's short name, like the README title).

**Why:** everything sits under the shared `oppshan` umbrella (sibling app `oppshan-files`, the
`oppshan.com` domain, the one `oppshan` Neon DB). Top-level AWS names share a flat account namespace
so they need the prefix to avoid collision; anything already inside an `oppshan` container would just
read redundantly (`oppshan/oppshan-washa`), so it stays `washa`.

**How to apply:** when adding or renaming a deployment resource, ask "does its container already say
`oppshan`?" — no (top-level AWS resource) → prefix `oppshan-washa`; yes (inside the oppshan
DB/project/domain/package/SSM tree) → leave `washa`. After any rename, grep the whole repo including
gitignored files (`(?<!oppshan-)washa-…`, `ap-…`, `(?<!OPPSHAN_)WASHA_…`) and confirm only the
namespaced/intended forms remain.
