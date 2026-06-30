Polish the README.md so it accurately reflects the current codebase, reads like a human wrote it,
and hits every rubric criterion at "Exceeded Requirement."

Work through each phase below in order. Do not skip phases.

## Phase 0: Rubric alignment check

Read the rubric at `docs/misc/rubric.md`. For each of the 7 criteria, the README must clearly demonstrate
"Exceeded Requirement" — a professor skimming should find the evidence without digging.

Map each criterion to the README sections that satisfy it:

| # | Criteria | README section(s) that must demonstrate it |
|---|----------|--------------------------------------------|
| 1 | **User Stories well documented** | Project Management, User Stories table |
| 2 | **Framework used to develop** | Tech Stack, Frontend Architecture, Backend Architecture |
| 3 | **Source Control** | Repository links, CI/CD section |
| 4 | **Functional URL** | Repository links (live URL), UX Design section |
| 5 | **Continuous Deployment** | CI/CD > Continuous Deployment section |
| 6 | **Dynamic Data Delivery** | Data Model, API Reference, persistence/query path |
| 7 | **User Experience and Styling** | UX Design section with wireframes |

Scan the README for each criterion. If any is weak, underdeveloped, or buried, flag it in the Phase 5 report
so the user knows what to strengthen.

## Phase 1: Source-of-truth audit

Read the current README.md in full. Then verify every factual claim against the actual source code:

- **Counts and names** — number of listeners, event types, dialogs, endpoints, entities, migrations, etc. Grep or read the source to confirm. Prefer language that won't go stale over hardcoded counts (e.g. "dedicated listeners" instead of "13 listeners") unless the count is stable and meaningful (e.g. "five core entities", "six migrations").
- **Descriptions of behavior** — how the event bus dispatches, how auth/session works, how the build pipeline runs. Trace the actual code path.
- **File layouts** — every tree listing in the README. Run `ls` or `find` against the real directory and compare.
- **API table** — every path, method, and purpose. Cross-check against the endpoint classes.
- **Tech versions** — Java, Quarkus, Angular, Node, PostgreSQL, etc. Check pom.xml, package.json, angular.json, Flyway migrations.
- **CI/CD descriptions** — workflow file names, steps, actions used. Read the actual `.github/workflows/` YAML.
- **Data model** — entity fields, constraints, view record fields. Read the actual Java source.
- **User stories table** — status column vs. what's actually merged or in progress.

For each conflict found, stop and ask the user how to resolve it. Present:
1. What the README says
2. What the code actually does
3. Your recommendation

Do not silently fix conflicts — the user may have a reason for the current wording, or the code may be the thing that's wrong.

## Phase 2: Humanize

Scan the **entire** README — not just paragraphs touched in Phase 1. Rewrite anything that sounds stiff, generated, or like a marketing datasheet.

**Target voice:** A student who's genuinely learning but confident in the technical decisions they made. Not boastful ("this project masterfully implements..."), not self-deprecating ("this is just a simple..."), but honest — someone explaining what they built, why they made each choice, and what they learned along the way. Think "I chose X because Y" energy, not "X was selected due to Y" energy.

**Bold style:** Use bold for **key ideas, technical decisions, and terms a professor would scan for** — the things that demonstrate framework mastery, architectural choices, and rubric-relevant capabilities. Bold is how the reader's eye finds the important parts. Do NOT strip bold from genuine key concepts. Only remove bold that's purely decorative or that bolds every other phrase in a paragraph to the point where nothing stands out.

Good bold: "every request runs on a **virtual thread**", "served as a **GraalVM-native AWS Lambda**", "**recursive CTE named native queries**"
Bad bold: "**The** application **uses** a **database**"

Watch for:
- Em dashes (—): Minimize or eliminate. Rewrite as two shorter sentences, use a colon, a comma, or parentheses instead. One or two in the whole README is acceptable if nothing else reads as cleanly; more than that reads generated.
- Passive voice where active is more natural ("the build is driven by Maven" → "Maven drives the build")
- Formulaic connectors ("furthermore", "additionally", "it is worth noting")
- Noun pile-ups ("single self-contained deployable artifact binary" → just say what it is)
- Fancy words where a simpler one works ("utilizes" → "uses", "facilitates" → "handles", "leverages" → "uses", "encompasses" → "covers", "provisions" → "sets up")
- Repetition of the same sentence structure across consecutive paragraphs
- Sentences that sound like they came from a product page or a generated summary rather than a person

Previous sentences that were never edited are fair game — scan everything.

## Phase 3: Scrub internal tooling references

Search the README for any mention of:
- Superpowers, brainstorming sessions, or skill invocations
- Plan documents (`docs/superpowers/plans/`, `docs/superpowers/specs/`)
- CLAUDE.md files (root, backend, frontend, migrations)
- `.claude/` directory, commands, hooks, settings
- `.remember/`, memory files, or session logs
- Any other internal tooling that a reader (professor, reviewer) should not see

Remove or rewrite any such references. The README is the public face of the project.

## Phase 4: Diagrams

Search the README for any Mermaid code blocks (` ```mermaid `) or other text-based diagram formats (ASCII art, PlantUML, etc.).

For each one found:
1. Convert it to a hand-drawn SVG following the conventions in `docs/diagrams/` — read an existing SVG first to match: font family (Segoe UI), color palette (green/teal for frontend, orange for backend, blue for client, purple for database, `#FAFAFA` background), arrow markers, rounded corners, dashed boundary lines.
2. Save the SVG to `docs/diagrams/<descriptive-name>.svg`.
3. Replace the code block with `![Alt text](docs/diagrams/<descriptive-name>.svg)`.

If no text-based diagrams remain, skip this phase and say so.

## Phase 5: Report

Output a summary:
- **Rubric coverage:** for each of the 7 criteria, state whether the README clearly demonstrates "Exceeded" and where. Flag any that are weak.
- **Conflicts found:** list each, with resolution chosen
- **Humanized:** list of sections rewritten (or "none needed")
- **Scrubbed:** list of internal references removed (or "clean")
- **Diagrams converted:** list of new SVGs (or "none found")
- **PDF regenerated:** "yes" if Phase 6 ran, "skipped (README unchanged)" otherwise
- **No changes:** if the README was already accurate and polished, say so

## Phase 6: Regenerate the PDF

If `README.md` was modified in any earlier phase (Phase 1 conflict fix, Phase 2 humanization, Phase 4 diagram swap), regenerate `README.pdf` so the PDF artifact stays in sync with the source.

Run from the repo root:

```bash
npx --yes md-to-pdf README.md
```

This overwrites `README.pdf` in place. If `README.md` was untouched, skip this phase entirely — do not regenerate. Note the outcome in the Phase 5 **PDF regenerated** line.