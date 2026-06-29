# Personal workflow rules

This file holds workflow preferences for working with Claude on this repo. Code conventions live in the standard `CLAUDE.md` files (root, backend, frontend, migrations); this document captures only how the maintainer (Warren) wants Claude to *behave* while collaborating, not how the code itself is structured. It's checked into the repo so a fresh `git clone` on another workstation picks them up automatically.

## Git — Claude commits per task

For washa, Claude commits per task on the feature branch (`feat/walking-skeleton`). Stage the files the task touched, write the commit (see § Commit messages), and commit. Warren reviews the result and the work gets pushed. There is **no read-only-git restriction** here, and `permissions.deny` in `.claude/settings.json` is intentionally empty so the committed settings never block a per-task commit. Don't change git config, don't run `git init`, and don't push unless Warren asks; branch creation, merges, and pushes stay with Warren.

**Parallel sessions share the working tree.** When a second Claude session is active on this repo (e.g. a PGO session alongside the deployment/UI one), commit only explicit paths — `git commit -- <path> ...` — so a bare `git commit` doesn't sweep the other session's staged files into yours (it once swept a staged `config/Person.java` into a docs commit). Stay off the files the other session owns. See `.claude/memory/feedback_parallel_session_commits.md`.

## Commit messages

Claude writes the commit message when committing a task. Three formatting rules:

- **Past tense, not imperative.** Match the sibling project's `git log` style: `refs #19 Fixed deployment NullPointerException`, `refs #19 Refactored the OIDC session manager`. Not "Fix" / "Refactor". Other verbs to use: `Added`, `Removed`, `Documented`, `Updated`, `Renamed`, `Implemented`, `Reverted`, `Superseded`.
- **No Claude / AI attribution.** Plain past-tense messages only. No `Co-Authored-By: Claude` trailer, no "Generated with" / "🤖" line, no mention of Claude, Anthropic, or any AI tooling anywhere in the subject or body.
- **No manual line wrapping in the body.** Soft-wrapped lines drag along leading whitespace from any indentation. Write one long unwrapped line per logical paragraph, separated by blank lines. The terminal/git client wraps visually.

Format:

```
refs #N <one-line past-tense summary>

<optional one long unwrapped paragraph explaining why; one more paragraph if there's a second logical concern>
```

Accepted prefixes:

- `refs #<issue-number> <description>` — feature / refactor / bugfix
- `chore: <description>` — automated / housekeeping
- `refs #<n> Merged feat/EPIC-0x: <description>` — epic merge commits

If a reply touches two unrelated concerns, propose two commits, not one conflated one. If a change is a follow-up correction to a prior commit, mark it explicitly: `Supersedes <sha>`, `Completes <sha>`.

## Behavioral defaults

How Warren wants Claude to *behave* during collaboration on this repo. Code conventions sit in the standard `CLAUDE.md` hierarchy; the rules below are about process, tone, and verification — they survive across stories and would otherwise live per-machine in auto-memory.

### Writing tone

- **Humanize wording.** Write in a natural voice — a student who learned the material, not a product page. Target: "I chose X because Y" energy, not "X was selected due to Y." Use contractions, active voice ("Maven drives the build" not "the build is driven by Maven"), and plain verbs ("uses", "handles", "sets up", not "utilizes", "facilitates", "provisions"). Scan older sentences too; previously-unedited text can be just as stiff. Keep **bold** for key concepts a professor or reviewer would scan for; only strip it when nothing stands out anymore.
- **Minimize em dashes in narrative.** Rewrite em dashes inside flowing sentences as colons, commas, two sentences, or parentheses. Leave them alone in markdown headings (`### Populated drive — list view`), TOC anchor titles, proper-noun strings (`ITMD 504 — Programming and Application Foundations`), and cardinality markers in diagrams (`1 — N`). One or two narrative em dashes in an entire document is fine when nothing else reads as cleanly.

### Audits and code-review follow-up

When `/audit-backend` or `/audit-frontend` runs and Warren asks for proposed fixes, treat it as a deliberate three-phase loop:

1. **Cover every open finding** — apply OR explicitly drop with rationale. Partial coverage forces a second ask.
2. **Propose before editing.** Print before/after code blocks in chat; do not apply edits until Warren says "apply", "go ahead", "do it".
3. **Flush to a spec doc** at `docs/superpowers/specs/YYYY-MM-DD-audit-findings.md` when asked. Structure: summary count table at top → per-finding entries with `file:lines`, **Status:** Apply | Drop, before/after code blocks, rationale → consolidated drop list → suggested commit batching order. Audit deliverables get reviewed offline; chat scrollback is not the artifact.

**Audit-rule context.** Before applying an audit finding, identify the specific failure mode the rule was written to prevent. If the case at hand cannot manifest that failure (immutable record, single-callsite construction, no cross-context payload flow), say so and skip — don't apply rules as universal mandates. Same principle for security findings: lead with the threat model, not generic best-practice. This app is a two-user household budget app running as a GraalVM-native AWS Lambda (arm64) behind CloudFront with OAC-signed Function URL access, backed by Neon PostgreSQL, in ap-southeast-1 (ACM cert in us-east-1). No EC2, no SSH, no file storage. The user set is closed (allowlist-gated Google OIDC). Show the reasoning so Warren can confirm or correct it; don't silently downgrade severity.

### Verification before claiming done

- **Screenshot-verify the UI against the prototype before every UI commit.** `tokyo_budget_tool.html` (repo root) is the layout-and-behavior spec for the Angular app. When checking any UI change, drive the running app in a headless browser and screenshot it, and capture the matching prototype view in the same pass so the two sit side by side; never judge the app's screenshot on its own. The app must mirror the prototype down to the minute details: component structure, alignment, spacing and gaps, distances, dashed-input extents, control placement, and behavior (dialog vs inline row, slider feel, live vs deferred figures). The only sanctioned divergence is the color palette (washa's amber standing in for the prototype's green); match everything else, and run as many passes as it takes. Drive the app without a pasted session cookie by standing up a local-only OIDC-bypass dev profile against a throwaway Dev Services Postgres (uncommitted scaffolding, torn down and `.env` restored afterward). The local Angular dev server does not reliably recompile and hot-reload source edits in this setup, so restart it after each batch of UI changes before screenshotting; otherwise the screenshots show stale pre-edit output and the verification is worthless. Restarting the frontend server alone preserves the Dev Services data; only restarting the backend resets the DB.

- **Rendering a specific prototype chart view headless.** The prototype is one interactive HTML that defaults to the Bars chart, so a bare `--screenshot` only catches the top in Bars view. Copy it to `/tmp`, inject a `<script>` that on load calls `BUD.chart('pie'|'flow')` (plus `el.scrollIntoView()` for a below-the-fold section), then `google-chrome --headless --screenshot --virtual-time-budget=4000`. It holds real data — don't reproduce figures.

- **Don't combine `./mvnw -q` with the maven hook.** `.claude/hooks/filter-maven.sh` rewrites every Maven invocation to grep for build/test status and stack-trace lines (e.g. `BUILD (SUCCESS|FAILURE)`, `[ERROR]`, `Tests run:`, `Caused by:`, `at com.oppshan.washa`). Quiet mode strips `[INFO] BUILD SUCCESS`, so the filter produces empty output even on a passing build — looks indistinguishable from a hang or a silent failure. Drop `-q` whenever you need to confirm a Maven invocation succeeded; reserve `-q` only for dependency-tree / version-resolution commands whose output you don't read.

- **Render diagrams to view.** After any SVG edit in `docs/diagrams/`, render to PNG via Chrome headless and `Read` the PNG — Claude reads PNGs as multimodal input. Coordinate math doesn't catch marker scaling, label-vs-line overlap, or marker skew on diagonal lines.

  ```bash
  google-chrome --headless --disable-gpu --no-sandbox \
    --window-size=940,860 --hide-scrollbars \
    --screenshot=/tmp/diagram.png file:///abs/path/to.svg
  ```

  Avoid ImageMagick `convert` for SVG-to-PNG — weak SVG support, breaks `markerUnits="userSpaceOnUse"`, garbles multi-line text with tspan elements.

- **Hash-verify external uploads.** After Warren uploads an artifact to a portal (Coursera, S3, anything), fetch the result with `curl -sSL -o /tmp/<file>` and compare with `sha256sum` plus `pdfinfo` (`CreationDate`, `Pages`, `File size`) against the local source. Don't ask him to eyeball it. Stale-upload (wrong source file picked) is more common than platform re-encoding; the difference is usually invisible to a human scroll-through.

- **Verify recommendation prereqs before pitching.** When proposing options with one labeled as "reuse the existing X" or "the lightweight path," budget 30 seconds before the message to grep + read confirming X actually fits the use case. The "easy" choice is exactly where confidently wrong recommendations slip in (e.g., "use the existing `@QuarkusIntegrationTest` suite" pitched without first checking there are zero such classes). If verification reveals the easy option doesn't exist, surface that honestly rather than papering over. Same principle applies to framework default values stated from memory: defaults drift across releases (Quarkus Agroal `max-size` is **50** in 3.34.x, not the older "conventional wisdom" of 20), so grep the relevant `@WithDefault` annotation in the sources jar before quoting a default authoritatively, and when Warren corrects you with the actual source, acknowledge the miss instead of explaining-away why your earlier guess was reasonable.

- **Check compatibility before bumping a dependency.** Latest-on-the-registry is not "safe to apply." Before bumping any dependency or plugin version, verify it fits with the framework or runtime it plugs into:
  - **Quarkus-managed dependencies** — check the Quarkus BOM for the target Quarkus version before overriding. Don't pin a version that the BOM also pins, unless the override is intentional and the BOM resolves higher than what we want.
  - **Angular ecosystem** — every `@angular/*` package must move together within the same major. `@angular/build` and `@angular/cli` can lag core's patch within the same major. Verify TypeScript bumps against `@angular/compiler-cli`'s and `@angular/build`'s `peerDependencies.typescript` range (e.g., Angular 22 → `>=6.0 <6.1`, so 6.0.x is permitted but 6.1+ is not; whichever package has the stricter cap wins). Verify Node bumps against `@angular/core`'s `engines.node`, and mind gaps in it — Angular 22's `^22.22.3 || ^24.15.0 || >=26.0.0` excludes Node 25.x. When asked for "the latest supported" version, pick the highest release that satisfies the range, even a non-LTS Current one (see `.claude/memory/feedback_latest_supported_version_selection.md`). Verify RxJS bumps against the Angular major's supported range.
  - **Test-scope libraries** (htmlunit, rest-assured, etc.) — these aren't managed by Quarkus BOM and we pin them directly. Confirm the specific imports we use remain present across the version jump; major-version bumps may rename packages (e.g., htmlunit's `com.gargoylesoftware.htmlunit` → `org.htmlunit` happened in 3.x→4.x).
  - **Major version bumps** — default to skipping unless there's a driving reason. Patch and minor bumps within the same major are usually safe; majors get their own decision. Document the skip in chat so Warren can override.
  - **Source of truth** — Maven Central's Solr search (`search.maven.org/solrsearch/select`) ranks by relevance, not date — query `repo1.maven.org/maven2/<group-path>/<artifact>/maven-metadata.xml` instead for the actual newest. npm registry's `/<package>/latest` endpoint is reliable.

  Always state the compatibility check you ran in the diff message ("Angular 22 peer dep `>=6.0 <6.1` confirmed → TS 6.0.3 within range"). Silent bumps are how `MissingReflectionRegistrationError`-class breakages slip in.

- **Acknowledge unreadable media upfront.** When Warren shares an mp4 / mov / audio / binary file, state the limitation explicitly **before** sketching a code-only hypothesis. The Read tool handles PNG / JPG / GIF / PDF; it doesn't open mp4 / mov / audio. Offer the conversion path:

  ```bash
  # VLC scene filter (snap-confined; CANNOT write to /tmp — use ~/vidframes/)
  vlc -I dummy --no-audio --video-filter=scene --vout=dummy \
      --scene-format=png --scene-ratio=15 --scene-prefix=frame \
      --scene-path=/home/warren/vidframes "input.mp4" vlc://quit
  # ImageMagick (no ffmpeg dependency)
  montage frame*.png -tile 8x10 -geometry 200x+2+2 contact.jpg
  convert -delay 25 -loop 0 -resize 400x frame*.png recording.gif
  ```

  The Read tool renders only the first frame of an animated GIF — use the contact sheet to find moments of interest, then Read individual downscaled PNGs at those frame numbers. The code hypothesis is a fallback to verify the recording against, not a substitute.

### Documentation sync

- **Fix code to match docs, not vice versa.** When a README claim diverges from code, default to bumping the code (e.g., add `sealed` to an interface, tighten a constraint). The README is the submission artifact and grading source of truth. Only weaken the README if the code change would be risky or architecturally wrong.
- **README schema sync.** The README's "Data model" section is a narrative summary of the model's *shape* (one shared month owning income/expenses/goals/debts, a normalized payroll engine, derived cumulative figures), not per-entity or per-migration tables. Sync the shape, not every field: when a change adds an entity or relationship, adds a Flyway migration, or otherwise alters that high-level shape, update the narrative in the same commit. Routine field, constraint, or nullability tweaks need no README edit, since the narrative doesn't enumerate columns. (Were the README to grow per-entity / view-type / migration tables, restore the per-table sync this rule once described.)

- **Deployment values stay in sync.** When changing infrastructure values that appear in more than one place (the CloudFront distribution, the Lambda Function URL + OAC config, the Neon connection settings, the ACM cert region, IAM policies, GitHub Actions workflow, or any `${ENV_VAR}` name in `application.properties`), update every copy together and grep for the old value across the repo after the edit.
- **No auto-regen during prose polish.** When iterating on README, README-summary, design docs, don't auto-regenerate downstream artifacts (PDFs, mockups, screenshots) until wording is settled. Warren does several rapid passes; re-rendering PDFs against not-yet-final prose wastes his reading time and creates pressure to lock in wording prematurely. Exception: an explicitly batched request ("fix X, Y, Z and regenerate").
- **Respect section boundaries when editing structured files.** `application.properties`, README, CLAUDE.md, pom.xml, conventions docs — anything organized into named sections — has an implicit "the right place for this thing lives in section X". When adding a property, table row, command, or paragraph, find the section it belongs to (HTTP property → HTTP section, datasource property → Datasource section, frontend convention → frontend CLAUDE.md). Don't pile new content at the bottom of the file, in the middle of an unrelated section, or wherever the last edit happened to land. If no matching section exists, propose creating one rather than inlining into an adjacent section.

### Scope, scripts, and specs

- **One-off scripts go in `/tmp` with `chmod 700`.** Scripts that perform mutations Warren runs himself (gh issue edits, batch ops) belong at `/tmp/<descriptive-name>.sh`, never the repo. Embed an audit trail in comments (originals, rationale) so the script is self-documenting if Warren keeps a copy. Hand him the path; he reviews and invokes via `! bash /tmp/<name>.sh`. **The `/tmp` script convention is for Warren-invoked mutations only.** For Claude-internal read-only batch work (registry version queries, repeated curls across a list of packages), run them as parallel Bash tool calls in a single message — that's faster, leaves the work visible in the transcript, and doesn't require a separate review step. Don't bundle them into a helper script.
- **Scope defensive cleanup to the environment that needs it.** Teardown traps, finally blocks, and safety hooks belong in environments where the failure mode they catch can actually happen. Ephemeral CI runners (GitHub-hosted `ubuntu-24.04-arm`) reclaim the VM after each job, so `docker compose down -v` and similar teardown is dead code in CI. Before adding a try/finally, trap, or shutdown hook, ask whether the environment already cleans up by itself; if it does, the hook isn't free, it's noise someone has to skip past when reading the code. A local-dev wrapper that stops orphaned Dev Services containers on Warren's machine is worth it; the same teardown in a workflow that runs on a discarded runner is not.
- **Specs go in `docs/superpowers/specs/`, not CLAUDE.md.** Story-specific artifacts (AC originals before a rewrite, decision logs, audit trails, design rationale) belong at `docs/superpowers/specs/YYYY-MM-DD-<descriptor>.md` matching the existing pattern (`2026-04-24-epic04-file-management-design.md`, `2026-05-04-epic05-context-menu-design.md`). CLAUDE.md is for evergreen conventions only; it loads into context every prompt, so pinning story content there bloats the always-loaded index.
- **`.remember/*.md` files are append-only.** `now.md`, `today-*.md`, `recent.md`, `archive.md`, `core-memories.md`, `remember.md` accumulate session log entries; never truncate-and-replace. New content goes at the end (or the documented position for that file), preserving everything already there. This applies even when a skill's instructions say "overwrite" — the maintainer's append rule wins. The `Skill(remember:remember)` invocation handles the right insertion position for `now.md` / `today-*.md`; for direct edits, read the file first and add a new section.
- **Outcome-focused acceptance criteria.** When drafting or rewriting ACs (GitHub issues, spec docs, README story lists), strip prescriptive measurements (`0.25rem grid`, `320px viewport`, "minimum tap target size"), specific component lists ("dialogs, notification center, toolbar"), and how-to wording ("media queries", "base styles target small screens"). Describe what a user observes: "feels native on a phone", "spacing feels consistent", "interactive elements are easy to tap accurately", "no horizontal scroll on a typical phone viewport." Preserve originals in `/tmp` script comments or spec docs.

### Prod data safety

- **Non-destructive prod data fixes.** When prod data violates a soon-to-be-added constraint (UC, NOT NULL, CHECK), present rename / disambiguate as a peer option to delete, default lean: rename. A close content match doesn't justify deleting the wrong row. Propose deterministic, conflict-checked suffix schemes and verify no collision before renaming. Always offer the non-destructive option first; don't recommend delete as the default unless Warren has explicitly authorized destructive ops for the task. There is no prod shell (washa runs as a Lambda, not on an instance); prod data changes go through a Flyway migration or a reviewed one-off, not an ad-hoc SSH/SSM session.

### Diagrams and HTML tables

- **Diagram conventions** live alongside the SVGs at `docs/diagrams/conventions.md` — orthogonal lines, crow's-foot markers, FK→PK direction, color palette, render-to-verify step. Read before touching anything under `docs/diagrams/`.
- **HTML table widths.** Use `<col style="width: 22%;">`, not deprecated `<col width="22%">` — the IDE flags `width="X"` on `<col>`/`<th>`/`<td>` as obsolete HTML5. Trade-off: GitHub strips inline `style` from rendered markdown so columns auto-size on github.com; IDE preview, browser-rendered raw `.md`, and PDFs from those previews honor the explicit widths. Give matching tables the same `<colgroup>` so the auto-size fallback still produces visually similar columns.

## Memory lives in the repo, not in user-level auto-memory

All durable observations — feedback, conventions, references, in-flight project state — must be written to a file committed to this repo, not to `~/.claude/projects/.../memory/`. Auto-memory is per-machine and doesn't survive `git clone` on another workstation; that's the wrong substrate for anything we want to carry forward.

Where to write a new observation:

| Type | Destination |
|---|---|
| Workflow behavior, tone, verification rules | `.claude/CLAUDE.md` (this file) |
| Backend code conventions | `src/main/java/CLAUDE.md` |
| Frontend code conventions | `src/main/angular/CLAUDE.md` |
| Migration conventions | `src/main/resources/db/CLAUDE.md` |
| Cross-cutting (MessageCode contract, deployment) | `CLAUDE.md` (root) |
| In-flight project state (audit findings, design decisions, EPIC status) | `docs/superpowers/specs/YYYY-MM-DD-<descriptor>.md` |
| Cross-conversation feedback patterns (captured by `/flush-context`) | `.claude/memory/feedback_*.md` indexed in `.claude/memory/MEMORY.md` |

**Exception: per-machine workflow.** Personal-machine preferences (editor tab width, terminal shortcuts, local IDE tweaks) belong in user-level auto-memory. If a rule wouldn't generalize to another developer who clones this repo, it isn't project memory.

Before writing a new rule, check whether one of the destinations above already covers the topic — extend the existing section rather than creating a new file. When in doubt about where something belongs, propose the destination in chat first; the maintainer will redirect.

The auto-memory tooling (`/remember`, `Skill(remember:remember)`, `Skill(flush-context)`) may still be used as a short-lived scratch pad inside a single conversation, but anything worth carrying to the next session must be flushed to the repo before the conversation ends.

### When extending the doc hierarchy

- **Co-locate convention files with the artifacts they govern.** Diagram conventions live next to the SVGs (`docs/diagrams/conventions.md`), icon palette next to the icons (`src/main/angular/public/icons/conventions.md`). `.claude/` is reserved for files Claude auto-loads (`.claude/CLAUDE.md`, slash commands, hooks, settings) — not general reference material that humans also read.
- **Strip Claude-specific framing from docs that live outside `.claude/`.** A file at `docs/operations.md` or `docs/diagrams/conventions.md` describes project conventions for any reader. Avoid Claude tool names (`Read the PNG`, `Skill(...)`), references to `.claude/settings.json` `permissions.deny`, or framing like "kept here so a fresh clone has everything Claude needs." Write so a human collaborator joining the project can use the doc without translating; Claude is one of many readers.
- **Lowercase markdown filenames** under the project root. The exceptions are platform / convention-mandated uppercase: `CLAUDE.md` (Claude Code auto-loads this name), `README.md`, `MEMORY.md`, `LICENSE.md`. Everything else — `conventions.md`, `operations.md`, `architecture.md`, etc. — is lowercase. Node-modules and other dependencies are out of scope.
- **Root `CLAUDE.md` carries a navigation index.** The "Where to look" table at the top of `CLAUDE.md` lists every CLAUDE.md, conventions.md, and operations doc in the repo with a one-line scope blurb. Add a row when creating a new top-level conventions file; remove a row when one is deleted or merged.