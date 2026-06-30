---
name: feedback_docs_vs_actual
description: washa's CLAUDE.md docs describe oppshan-files-derived patterns that washa never built (event bus, UserSessionManager, SPA filter, ngx-translate); verify documented patterns against actual code, and use signals not the bus
type: feedback
---

A recurring, costly gotcha this session: several things the washa `CLAUDE.md` files document as
existing were **specced from oppshan-files but never actually built** in washa. This session had to
create `UserSessionManager` + `OidcUserSessionManager`, the `FrontendRoutesFilter` SPA fallback, and
wire up **ngx-translate** — all referenced in docs as if present. The frontend `CLAUDE.md` still
describes a full **event-bus / CQRS** architecture (`MessageBusService`, `ApplicationEvent`,
`MESSAGE_LISTENERS`, listeners, `class-transformer`, `JsonMapper`) that washa **does not implement**.

**Rule — verify documented patterns against the actual code before relying on them.** Grep for the
class/symbol; if it doesn't exist, the doc is aspirational. Don't cite a documented helper, default,
or architecture as fact without confirming it's built. (A background subagent audit was even misled
into "dialogs must use the bus per CLAUDE.md" — wrong; washa uses signals.)

**What washa actually uses (frontend):**
- **Signals + a signal store**, not the bus. `BudgetStore` (`signal`/`computed`/`mutate`) owns budget
  state; pages call `store.method()`; `BudgetApiService` is a thin HTTP wrapper. The functional
  `authGuard`/`guestGuard` call `/api/me`. No `MessageBusService`, listeners, or `class-transformer`.
- **Dialog pattern (signal-based)** — established across the salary/goal/debt edit dialogs: the dialog
  takes the entity via `input.required`, holds a `linkedSignal` **deep-clone draft** (edits stay local;
  cancel discards), mutates the draft via a `patch()` helper, and emits `saved`/`cancelled` via
  `output()`. The page holds an `editingXIndex = signal<number|null>` and mounts the dialog with `@if`,
  applying the emitted entity through `store.mutate`. Reuse the ported `.modalwrap`/`.modalcard`/
  `.fld`/`.editrow`/`.bkt-*`/`.ratestep` styles in `styles.scss`.

**What washa actually uses (backend auth):** `UserSessionManager` is `@RequestScoped` (so the injected
`SecurityIdentity` resolves request claims under `@TestSecurity`); `OidcSession` is injected as an
`Instance<>` (the test profile disables OIDC, removing the bean); there is **no** `SessionScoped`
caching (a CloudFront+Lambda has no sticky sessions). Sign-out is a **local** logout
(`OidcSession.logout()`) because Google advertises no `end_session_endpoint`; `/sso/sign-in` is a
**frontend** page (guarded by `guestGuard`) while the backend OIDC trigger is `/sso/sign-in/oidc/google`.

**Why:** treating the docs as ground truth wasted time and produced a confidently-wrong subagent
finding; the docs lag the build because washa intentionally diverged from oppshan-files (simpler is
right for a two-user app).

**How to apply:** when a doc names a pattern/helper, confirm it exists before using it; reconciling the
frontend `CLAUDE.md` to the signal-store reality is still an open task. See [[feedback_portal_and_design]].
