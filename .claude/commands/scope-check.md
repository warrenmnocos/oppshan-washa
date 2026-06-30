Review the current branch diff for scope discipline.

Run `git diff main...HEAD --stat` to see what files changed, then `git diff main...HEAD` for the full diff.

Report:
1. **In-scope changes** — directly address the current user story / epic branch goal.
2. **Questionable changes** — may be justified but worth flagging (refactors, style fixes touching many files).
3. **Out-of-scope changes** — pre-existing issue fixes, unrelated improvements, scope creep.

For each questionable or out-of-scope change, note the file and line range, and suggest whether it should be reverted, extracted to a follow-up branch, or is actually justified.

Keep the report under 300 words.
