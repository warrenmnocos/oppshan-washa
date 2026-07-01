Survey every backend, frontend, and CI/CD dependency version against the upstream registry, propose a bump plan grouped by risk, and apply approved bumps to `pom.xml`, `src/main/angular/package.json`, and `.github/workflows/*.yml`.

This sweep is read-heavy and benefits from running in the main session (the verification calls into `mvnw` and `yarn` need to land in conversation context for follow-up debugging).

## Required reading first

1. `.claude/CLAUDE.md` — the **"Check compatibility before bumping a dependency"** rule under § Verification before claiming done. Apply it to every line of the proposal.
2. `pom.xml` — version-bearing `<properties>` block (lines 10–70) and the `<dependencyManagement>` / `<dependencies>` / `<pluginManagement>` blocks.
3. `src/main/angular/package.json` — `dependencies` and `devDependencies` sections.
4. `.github/workflows/*.yml` — every `uses: <owner>/<action>@<ref>` line (today in `cd.yml` and `ci.yml`).
5. The root `CLAUDE.md` § C.4 mentions the Quarkus version once in the opening blurb and `src/main/java/CLAUDE.md` § A.1 mentions it again — both must move in lockstep with `quarkus.platform.version`.

## What to query

**Backend (Maven Central)** — query `https://repo1.maven.org/maven2/<group-as-path>/<artifact>/maven-metadata.xml` for each pinned coordinate. The Solr search at `search.maven.org/solrsearch/select` ranks by relevance, **not** date — do not use it as the source of truth for "latest." Pinned coordinates today:

| Property | Coordinate |
|---|---|
| `quarkus.platform.version` | `io.quarkus.platform:quarkus-bom` |
| `guava.version` | `com.google.guava:guava` (use the `-jre` variant) |
| `htmlunit.version` | `org.htmlunit:htmlunit` |
| `jakarta.data-api.version` | `jakarta.data:jakarta.data-api` |
| `maven.compiler.plugin.version` | `org.apache.maven.plugins:maven-compiler-plugin` (skip `4.0.0-beta-*`) |
| `maven.surefire.plugin.version` | `org.apache.maven.plugins:maven-surefire-plugin` |
| `maven.failsafe.plugin.version` | `org.apache.maven.plugins:maven-failsafe-plugin` |
| `jacoco.plugin.version` | `org.jacoco:jacoco-maven-plugin` |
| `exec.maven.plugin.version` | `org.codehaus.mojo:exec-maven-plugin` |
| `frontend-maven-plugin.version` | `com.github.eirslett:frontend-maven-plugin` |
| `node.version` | Node.js (https://nodejs.org/dist/index.json) — stay on the active LTS major by default; when Warren asks for "the latest supported", pick the newest release satisfying Angular's `engines.node` range, even a non-LTS Current one (mind range gaps, e.g. Node 25.x is excluded by Angular 22) |
| `angular.cli.version` | `@angular/cli` (npm registry) |

**Frontend (npm registry)** — query `https://registry.npmjs.org/<encoded-package>/latest`. Pinned packages today:

- All `@angular/*` packages (`common`, `compiler`, `core`, `forms`, `platform-browser`, `router`, `build`, `cli`, `compiler-cli`)
- `@ngx-translate/core`, `@ngx-translate/http-loader`
- `class-transformer`, `reflect-metadata`, `luxon`, `rxjs`, `tslib`
- `@types/luxon`, `jsdom`, `prettier`, `typescript`, `vitest`

URL-encode the `@scope/name` form as `@scope%2Fname` in the URL path.

**CI/CD (GitHub Actions)** — for each `uses: <owner>/<action>@<ref>` in `.github/workflows/*.yml`, the source of truth is the action's GitHub repo, **not** a package registry:

- **Latest release:** `gh api repos/<owner>/<action>/releases/latest --jq .tag_name`.
- **Newest major:** a `@vN` tag only moves within major N, so confirm N is itself the newest — `gh api repos/<owner>/<action>/tags --jq '.[].name' | head`. (This is the easy miss: pinning `@v5` when `@v7` exists still leaves you behind.)
- **Node runtime** — the usual driver to bump: `curl -s https://raw.githubusercontent.com/<owner>/<action>/<ref>/action.yml | grep -i 'using:'`. `node16` / `node20` are deprecated — GitHub force-migrates them onto a newer runtime and prints a deprecation warning on every run; `node24` is current, and bumping to a `node24` release is what clears the warning.

Pinned actions today (`cd.yml` + `ci.yml`): `actions/checkout`, `actions/setup-java`, `actions/upload-artifact`, `aws-actions/configure-aws-credentials`, `Madrapps/jacoco-report`.

## Compatibility rules (mandatory)

Apply the **"Check compatibility before bumping a dependency"** rule from `.claude/CLAUDE.md`. The short form:

1. **Quarkus BOM patch bumps** (3.x.y → 3.x.z) — safe; same minor follows semver. Quarkus minor bumps (3.x → 3.y) and majors require reading the release notes and verifying our `quarkus-*` extensions still resolve cleanly.
2. **Anything Quarkus already manages** — if the BOM pins it, prefer the BOM's version. Only override when the project deliberately wants to lead the BOM (currently none do).
3. **Angular packages** — every `@angular/*` package must move together within the same major. `@angular/build` and `@angular/cli` can lag core's patch within the same major. Read `@angular/compiler-cli@<target>`'s `peerDependencies` block; verify TypeScript, Node, and RxJS targets satisfy the listed ranges. Angular major bumps (21 → 22, etc.) are out of scope for this command — propose them as a separate spec doc.
4. **Test-scope libraries** (htmlunit, rest-assured) — confirm the specific imports we use survive the version jump. htmlunit's `com.gargoylesoftware.htmlunit` → `org.htmlunit` rename in 3.x→4.x is the canonical "test bump quietly broke imports" example.
5. **Major version bumps default to skipped.** Document the skip in the proposal with the version we're holding and why. Warren can override individually.
6. **Plugin betas** (e.g., `maven-compiler-plugin:4.0.0-beta-*`) — never auto-propose; only flag if Warren explicitly asks.
7. **GitHub Actions** — a major bump can drop or rename inputs, so `curl` the target `action.yml` and confirm every input we pass is still declared (setup-java's `java-version`/`distribution`/`cache`, upload-artifact's `name`/`path`, configure-aws-credentials' `role-to-assume`/`aws-region`). A bare `uses:` with no `with:` block (our `checkout`) is safe across majors — default changes don't reach us. Unlike Maven/npm majors, action majors are **in scope** here (a deprecated Node runtime forces them), but state the node runtime + input check in the note just the same. Track the newest major as a moving `@vN` tag (matches the repo style); pin an exact version only where the repo already does (`jacoco-report`).

## Output format (the proposal)

Print a comparison table grouped by file (`pom.xml`, `package.json`, `.github/workflows`). **One row per pinned property / package / action.** Columns: `name | current | latest | action | compatibility note`.

- `action`: `bump` / `stay` / `skip-major` / `skip-beta` — an action major does **not** get `skip-major` when a deprecated Node runtime forces the jump.
- `compatibility note`: the specific check you ran ("Quarkus BOM does not pin", "Angular 22 peer `>=6.0 <6.1` → 6.0.3 within range", "test-scope; `WebClient`/`HtmlForm` imports still present in 5.1.0", "checkout `@v5`→`@v7`: node24, bare usage, no inputs", etc.)

After the table, summarize:
- How many bumps proposed, grouped by patch / minor / major.
- Skipped majors (one bullet each, with reason).
- Doc-sync follow-ups: list every CLAUDE.md or README line that references a version literal we're bumping (grep `pom.xml`'s `quarkus.platform.version` across `*.md` first).

**Do not edit any file until Warren says "apply" / "go ahead" / "do it"** (matches the audit-follow-up convention in `.claude/CLAUDE.md`).

## Applying the bumps (after approval)

Edit in this order:

1. `pom.xml` — every property in one Edit. The `<quarkus.platform.version>` change should be checked against the Maven property's appearance count (`grep "quarkus.platform.version" pom.xml`) — there's a single source-of-truth property, but downstream references like `${quarkus.platform.version}` should not need touching.
2. `src/main/angular/package.json` — single Edit covering both `dependencies` and `devDependencies` blocks.
3. `.github/workflows/*.yml` — one `uses:` line per bumped action. These are one-token changes (`@v5` → `@v7`); a blanket `@v4`→`@v5` sed is **wrong** (different actions land on different majors), so edit each `uses:` line specifically.
4. Version literals in `CLAUDE.md` / `src/main/java/CLAUDE.md` / `README.md` — bump in lockstep. The root `CLAUDE.md` opening line and `src/main/java/CLAUDE.md` § A.1 both quote `Quarkus 3.x.y`.

## Verification (after applying)

Run these in order; surface failures verbatim, do not paper over:

```bash
./mvnw -q -DskipTests compile                  # backend compile
./mvnw -q test                                  # backend tests (Docker required for Testcontainers)
cd src/main/angular && yarn install             # frontend dep resolution
cd src/main/angular && yarn build --configuration development   # frontend build
```

Test failures or unresolved peer-dep warnings invalidate the bump — revert the affected property, mark it `stay` in the proposal, and re-print the table.

GitHub Actions bumps have **no local compile/test**. Verify them in the survey (the version exists, its `action.yml` declares `node24`, and our inputs are still present), then after editing confirm the YAML still parses (`python3 -c "import yaml; [yaml.safe_load(open(f)) for f in ['.github/workflows/cd.yml','.github/workflows/ci.yml']]"`) and `grep` shows no stale `@vN` ref remaining. The real runtime check is the next CI (PR) / CD (push to `main`) run.

## Constraints

- **Commit only after verification passes** (per `.claude/CLAUDE.md` § Git). A failed build/test invalidates the bump; revert the affected property and re-print the table before committing.
- **No `<dependencyManagement>` re-arrangement.** This command bumps versions; it does not restructure the pom.
- **Skip transitive analysis.** Only direct dependencies pinned by the project. Quarkus extension version diffs go through the BOM bump, not individual `<dependency>` blocks.
- **Don't propose unused-dependency removal here.** That's a different sweep; keep this one focused on version drift.
