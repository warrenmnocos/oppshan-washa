---
name: feedback_portal_and_design
description: washa is a multi-app household portal (budget is one app) — keep shared pages domain-agnostic; it shares files.oppshan.com's design language in its own amber palette, driven by CSS tokens + an app shell
type: feedback
---

Two related directions Warren stated repeatedly this session.

**Rule — washa is a portal, not a budget app.** `washa.oppshan.com` is a household portal that will
house multiple apps over time; the **Budget** app is just the first. So anything on the
**domain-agnostic pages** (dashboard launcher, sign-in, the shared header/footer) must read generically
about "your household / the things you run together", **never** budget- or money-specific. Only the
Budget app's own card description and the budget page itself talk about income/expenses/currencies.
(Warren's corrections: "washa is not just for budget… do not limit labels, messages, names to
budget-related concepts especially for dashboard, signin and other domain-agnostic pages"; "'One quiet
place for the money you share' sounds like washa is all about money management. It's not.")

**Rule — same design language as files.oppshan.com, different palette.** washa deliberately shares the
sibling product's design system (system-font type stack, accent-banded auth card, a logo-chip sticky
top bar, soft-shadowed rounded white cards, shimmer skeletons, mobile-first at the **37.5rem**
breakpoint with `pointer: coarse` touch targets) but in its **own warm amber/honey accent**
(`--accent:#B0651C`, `--accent-2:#D38A2E`) rather than files' teal — same company, different product.
The whole UI is driven by **CSS design tokens** in `styles.scss` (`:root` light + a
`prefers-color-scheme: dark` block), so re-theming is a token edit, not a sweep. The signed-in pages
are framed by an **app shell** (`AppShell`: flex column at `100dvh`, header/footer `flex:none`, a single
`overflow-y` content region) used as the parent route, so the fixed header and footer are retained when
moving between apps; the sign-in page sits outside the shell as its own full-screen layout. Units are
**rem, not px** (only hairline borders / mm print are exceptions). Verify visuals by rendering to PNG.

**Why:** the portal framing shapes copy, IA, and the dashboard's app-launcher layout; the design
direction keeps washa visually part of the oppshan family while clearly its own product, and the
token/shell architecture is what makes both cheap to maintain.

**How to apply:** before writing dashboard/sign-in/header/footer copy, strip budget/money words. When
restyling, edit `:root` tokens rather than component colors; reuse files' component patterns (read
`/home/warren/Projects/oppshan/files/src/main/angular`). New signed-in pages render inside `AppShell`
as child routes. See [[feedback_docs_vs_actual]] for the signal (not event-bus) architecture they use.
