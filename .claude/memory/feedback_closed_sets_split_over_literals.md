---
name: feedback_closed_sets_split_over_literals
description: Warren enforces the closed-string-sets rule even on UI-local values — never a `'a' | 'b'` literal-union discriminator param; for a value that never crosses the wire, prefer the split-into-two-methods escape hatch over minting a UI-only enum in models/
type: feedback
---

While porting the prototype's position-aware `scopeNamesAt(sal, kind, idx)` I wrote
`scopeNamesAt(kind: 'var' | 'ded', index)`. Warren caught it in review: "Is ded a candidate enum
value? Remember the rules about not using const literals when enums are appropriate." Resolved by
splitting into `scopeNamesForVariable(index)` / `scopeNamesForDeduction(index)` over a shared private
walk — the discriminator disappeared entirely — and he accepted without further comment.

**Rule:** the frontend convention (B.3) that closed string sets become enums applies to **UI-local
values too**, not just wire/domain values. But `models/` enums mirror the Java/JSON wire format 1:1,
so a value that never crosses the wire shouldn't become a models/ enum either — use B.3's own escape
hatch: split the two-way method into one method per kind, eliminating the discriminator param.

**Why:** literal unions are exactly what the convention exists to prevent, and Warren reads diffs
closely enough to catch one mid-task. The split reads better than either a literal union or a
wire-less enum, and matches how the codebase already avoids two-way setters.

**How to apply:** before writing any `'x' | 'y'` parameter, ask: does this value cross the wire? Yes
→ enum in `models/` mirroring Java. No → split the method per kind (or use an existing enum if one
genuinely fits). Mention the choice and the B.3 escape-hatch rationale in the reply so Warren can
redirect if he'd rather have the enum.