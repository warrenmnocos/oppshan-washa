---
name: feedback_apply_changes_exhaustively
description: When Warren asks to fix/rename/clean something cross-cutting, sweep ALL surfaces by default — tracked + gitignored files, source-code comments, gitignored historical design docs, diagrams — rather than scoping narrowly; flag only a genuine blocker
type: feedback
---

Warren values **exhaustive consistency**. In one rename session he three times widened the scope past
the conservative default I had proposed: "include code and gitignored files including env" (after I
held back the Java comments + `.env`), "rewrite them too" (overriding my "leave the historical design
docs as-is"), and "also update that" (the diagram I had left to the parallel session).

**Rule:** when asked to fix, rename, or clean something cross-cutting, **default to sweeping every
surface** — tracked AND gitignored files, source-code comments and javadoc, the gitignored `.env`,
gitignored historical design docs under `docs/superpowers/`, and SVG diagrams — not just the
tracked/operational files. Don't pre-emptively narrow on the grounds of "you said docs/scripts,"
"these are historical records," or "another session owns this." Surface a genuine blocker (a sentence
that would become self-contradictory, a file the parallel session is actively editing) as a quick
question, but otherwise be thorough on the first pass.

**Why:** Warren grades for parity and consistency across the whole repo; a half-applied rename that
leaves stale tokens in comments or gitignored docs reads as sloppy and forces another round-trip. One
extra clarifying question costs far less than repeatedly re-asking for wider scope.

**How to apply:** this is a *default*, not a licence to ignore the hard rules. Still commit explicit
paths and stay off the parallel session's STAGED files ([[feedback_parallel_session_commits]]); still
render-verify diagrams; still preserve genuine historical meaning (reword a "supersedes" line rather
than corrupt it; protect package paths with a lookbehind). For secrets-bearing gitignored files, edit
keys with an anchored `sed` so values never enter context ([[feedback_privacy_and_commits]]). After
the sweep, grep the whole repo (incl. gitignored, excl. `node_modules`/`target`) and report any
residue you deliberately left (e.g. a diagram the other session owns).
