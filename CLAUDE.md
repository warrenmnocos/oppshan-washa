# washa — Project Guide

A two-part app: a **dashboard** plus a **household budget app** for a closed two-person
household. Quarkus 3.36.1 (Java 25) backend + Angular 22 frontend, compiled into a single
native artifact that runs as an **AWS Lambda** (GraalVM-native, arm64) in **ap-northeast-1
(Tokyo)**, fronted by **CloudFront** with OAC-signed Function URL access. Persistence is
**Neon PostgreSQL**. Auth is **Google OIDC**, gated by a two-user allowlist. REST API under
`/api/**`; Angular assets served from the same runtime.

## Where to look

The conventions are split across the repo so Claude only auto-loads what's relevant to the directory you're working in. This file (`CLAUDE.md`) covers cross-cutting rules; everything else lives next to the code it governs.

| File | Scope |
|---|---|
| `README.md` | Main project doc and submission artifact: project overview, architecture, domain/view-type tables, migrations table, rubric-graded narrative |
| `CLAUDE.md` (this file) | Cross-cutting: MessageCode contract, commit conventions index, scope discipline, deployment overview, slash commands |
| `.claude/CLAUDE.md` | Workflow behavior: per-task commit workflow, commit message format, audit follow-up workflow, verification rules, memory policy |
| `.claude/memory/MEMORY.md` + `feedback_*.md` / `project_*.md` | Project memory (in-repo): cross-conversation feedback patterns and project-state snapshots. Captured by `/flush-context`; read by `/audit-backend` and `/audit-frontend` |
| `src/main/java/CLAUDE.md` | Backend Java / Quarkus conventions |
| `src/main/angular/CLAUDE.md` | Frontend Angular conventions |
| `src/main/resources/db/CLAUDE.md` | Flyway migration rules |

---

## Project at a glance

- **Auth:** Google OIDC (prod) / Keycloak (test). The user set is closed — a two-person allowlist gates sign-in.
- **Persistence:** Flyway-managed Neon PostgreSQL.
- **Tests:** Backend via Quarkus runtime (Dev Services / Testcontainers PostgreSQL — Docker must be running); frontend via `@angular/build:unit-test` (Vitest + jsdom). `./mvnw test` runs both; `-DskipTests` skips both.
- **Build:** `./mvnw quarkus:dev` (dev), `./mvnw test` (test), `./mvnw package` (prod native artifact for Lambda).

---

## Top-level layout

```
washa/
├── pom.xml          # Maven + Quarkus + frontend-maven-plugin
├── CLAUDE.md        # this file (cross-cutting only)
├── .claude/         # workflow rules, slash commands, hooks, settings
└── src/
    ├── main/
    │   ├── java/com/oppshan/washa/   # vertical-slice packages by domain
    │   ├── resources/                # application.properties, db/migration/postgresql/
    │   └── angular/                  # Angular 22 app (compiled via frontend-maven-plugin)
    └── test/java/com/oppshan/washa/  # mirrors main/java
```

---

## C.1 The `MessageCode` contract

The single cross-stack shared symbol, spread across three files:

- `src/main/java/com/oppshan/washa/exception/MessageCode.java`
- `src/main/angular/src/app/models/message-code.ts`
- `src/main/angular/public/i18n/en.json`

**TS is a superset of Java by design.** Java holds only the codes the backend emits (every value
corresponds to a `BusinessException` static factory, all of which map to HTTP 400 per A.7). TS
additionally carries frontend-only codes that never cross the wire:

- **Info codes** for success toasts — the backend returns the new view DTO; the frontend listener fires the toast itself.
- **Client-side validation** codes — rejection happens in the UI before any HTTP request.
- **Pure UI events** (`messages.info.signInSucceeded`, …) — router/lifecycle events with no server round-trip.

Do not add Java counterparts for these. Doing so would either lie about the backend's behavior
(dead enum entries) or force an architectural change for no real gain.

**Sync rules:**

1. Every Java value must exist in TS with a **byte-equal string value**, and in en.json as a non-empty translation. Java is the authoritative subset of backend-emitted codes; the wire contract breaks if it drifts from TS.
2. Every TS value (Java-mirrored or frontend-only) must resolve to a non-empty string in en.json. Missing en.json keys silently render as the empty string; missing TS-side enum entries silently degrade to `MessageCode.Unknown` on the frontend.
3. en.json may not contain `messages.*` keys with no enum referent on either side — those are dead translations.

Use `/sync-messagecode` to audit alignment. The command treats TS-only codes as informational, not as errors.

---

## C.2 Commit conventions

For washa, **Claude commits per task** on the feature branch (`feat/walking-skeleton`); Warren reviews and the work is pushed. Commit message format and the past-tense / no-line-wrap / **no-AI-attribution** rules live in `.claude/CLAUDE.md` § Commit messages. The three accepted prefixes are `refs #<n>` (feature/refactor/bugfix), `chore:` (automated/housekeeping), and `refs #<n> Merged feat/EPIC-0x:` (epic merges).

---

## C.3 Scope discipline

**Do not fix unrelated pre-existing issues** during a user story. File them as follow-ups.
Note them; don't sweep them up.

Use `/scope-check` to audit the current branch diff before opening a PR.

---

## C.4 Deployment

Production runs as a single **GraalVM-native AWS Lambda** (arm64) in **ap-northeast-1 (Tokyo)**,
fronted by **CloudFront** that reaches the Lambda **Function URL** with **OAC-signed** requests
(the Function URL is not publicly reachable on its own). The TLS cert is an **ACM certificate in
us-east-1** (CloudFront requires us-east-1 certs). Persistence is **Neon PostgreSQL**. There is
**no EC2, no SSH, and no file storage** — nothing to log into, nothing to patch at the OS level.

The native binary is built with `./mvnw package` against a GraalVM-native build (arm64, matching
the Lambda runtime). Deploys go through GitHub Actions; there is no long-lived AWS key and no
prod shell.

**Runtime env vars** (Quarkus runtime env var names, supplied to the Lambda — names match the
Quarkus property 1:1, no translation):

```
QUARKUS_OIDC_CLIENT_ID            QUARKUS_DATASOURCE_JDBC_URL
QUARKUS_OIDC_CREDENTIALS_SECRET   QUARKUS_DATASOURCE_USERNAME
QUARKUS_OIDC_TOKEN_STATE_MANAGER_ENCRYPTION_SECRET   QUARKUS_DATASOURCE_PASSWORD
```

Secrets are injected as Lambda environment variables, never hardcoded. The exact env-var set is
authoritative in `application.properties` (`${ENV_VAR}` references) — keep this list and the
properties file in lockstep.

---

## Slash commands

| Command | Purpose |
|---|---|
| `/sync-messagecode` | Audit Java enum ↔ TS enum ↔ en.json alignment |
| `/new-feature <description>` | Generate a backend + frontend implementation checklist |
| `/scope-check` | Flag out-of-scope changes on the current branch |
| `/audit-backend` | Sweep `src/main/java/` for correctness, security, performance, and convention deviations |
| `/audit-frontend` | Sweep `src/main/angular/src/` for bus discipline, signal usage, styling, and convention deviations |
| `/flush-context` | Persist in-flight project state to `docs/superpowers/specs/` before clearing the conversation |
| `/polish-readme` | Audit README against source code, humanize, scrub internal refs, convert text diagrams to SVG |
| `/upgrade-deps` | Survey backend (Maven Central) and frontend (npm registry) versions; propose a bump plan grouped by risk with compatibility notes; apply only on approval |
