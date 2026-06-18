Flush important session context to persistent storage before the conversation is cleared.

Scan the full conversation and perform each step below. Do not skip steps — context that isn't flushed is lost.

## 1. Feedback patterns

Look for every instance where the user:
- Corrected your approach ("no", "don't", "stop doing X", "why did you…")
- Confirmed a non-obvious approach ("yes exactly", "perfect", accepted without pushback)
- Expressed a preference about code style, tooling, workflow, or communication

For each, check `.claude/memory/MEMORY.md` (relative to the repo root: the project memory, committed with the repo so it travels across clones and machines). If the feedback already has a memory entry, skip it. If it's new, write a memory file following the existing `feedback_*.md` pattern (frontmatter with name/description/type, then rule + **Why:** + **How to apply:**) and add an index line to `MEMORY.md`.

Skip entries that are purely personal-machine workflow ("set my editor tab width to 4"): those belong in user-level auto-memory. The project-level repo memory is for things that apply to anyone who touches this codebase.

## 2. Project state

Identify what changed during this session:
- Features implemented or progressed (epic/story numbers, branch names)
- Bugs found or fixed
- Architectural decisions made
- New conventions or patterns established

Write or update a `project_*.md` memory file for anything non-obvious that a future session would need. Skip anything derivable from `git log` or the code itself.

## 3. CLAUDE.md updates

Check if any of the following changed during the session:
- Epic status (e.g., "not yet started" → "in progress" or "done")
- New slash commands added
- New cross-cutting conventions established
- Deployment or configuration changes

If so, list the specific edits needed to each CLAUDE.md file (root, backend, frontend, migrations). Apply them.

## 4. Session log (.remember)

Invoke the `/remember` skill to update `.remember/now.md` and the current day's `today-*.md` with a compact summary of this session's work.

## 5. Report

Output a concise summary:
- **Flushed:** list of new/updated memory files
- **CLAUDE.md:** list of edits made (or "no changes")
- **Session log:** confirmation that .remember was updated
- **Skipped:** anything you considered but decided was already captured or not worth persisting, with brief reason