---
name: feedback_parallel_session_commits
description: Two Claude sessions sometimes share this repo/working tree — commit only explicit paths (git commit -- <paths>), and stay off the files the other session owns (e.g. the PGO surfaces)
type: feedback
---

Warren sometimes runs **two Claude sessions on this repo at once** (this session: one on the AWS deployment + UI, a parallel one on the GraalVM PGO pipeline). They share a single working tree and git index.

**Rule:**
- **Commit only explicit paths: `git commit -- <path> [<path>...]`.** A bare `git commit` commits the WHOLE index, which can include files the OTHER session has staged. Lesson: a bare `git commit` swept the other session's staged `config/Person.java` into a docs commit; it had to be undone (`git reset --soft HEAD~1`) and re-committed with explicit paths. A narrow "no secrets staged" grep is not enough — it won't catch an unrelated staged source file. Always check `git diff --cached --name-only` is exactly your files, or just commit explicit paths.
- **Don't edit files the other session owns.** When told another session handles X, stay off those surfaces. PGO ownership this period: `scripts/graalvm-pgo/**`, the `%pgo.*` blocks in `application.properties`, the `native-release-pgo*` Maven profiles, `cd.yml`'s build path, `docs/native-image.md`, `docs/diagrams/**`. When restyling a shared file (e.g. reorganizing `application.properties` into sections), preserve their blocks verbatim and leave their sub-headers in place.
- **Shared docs both sessions touch** (`CLAUDE.md`, `README.md`, `.gitignore`): keep edits minimal and additive; re-Read before editing in case the other session changed them between your turns.

**Why:** avoids clobbering or mis-attributing the parallel session's work, and keeps each commit cleanly scoped to one session's task.

**How to apply:** treat the working tree as shared — never assume the index holds only your changes; scope every commit by path.
