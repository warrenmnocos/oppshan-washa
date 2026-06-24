---
name: feedback_proactive_tech_currency
description: Proactively surface modern features across ALL washa's libraries — frontend and backend, Angular and Quarkus and every dependency — that we could adopt; don't wait to be asked; verify against live docs, not memory
type: feedback
---

Warren wants me to **proactively point out new/modern features** in the frameworks and libraries washa
uses, rather than only building with what's already in the code. ("Don't hesitate to tell me what new
features we can use in frameworks and libraries we use, i.e. Angular 22." / "not just angular. all
libraries. including quarkus and backend/frontend.")

**Rule — this covers the WHOLE stack, both tiers:** Angular and its ecosystem (RxJS, ngx-translate,
Vitest, …) **and** Quarkus and the backend libraries (Hibernate ORM / Jakarta Data, Flyway, RESTEasy
Reactive, the OIDC extension, Mutiny, Testcontainers, …) — plus build tooling. When working in any area,
flag relevant modern capabilities of whatever library governs it that could simplify or improve the
code, with a short "could adopt / why". Tie the suggestion to the task at hand; don't dump a feature list.

**Why:** Warren is building a production-grade showcase app and values staying current; he'd rather hear
about a cleaner modern API than have me silently use the older pattern already in the file.

**How to apply:** verify the feature against **live docs** before recommending (context7 / the framework
changelog) — versions and stability drift, and the maintainer's verification rule forbids quoting from
memory (see [[feedback_docs_vs_actual]]). Note current adoption: washa already runs **zoneless**
(`provideZonelessChangeDetection()`), signals, `linkedSignal`, signal `input()`/`output()`, standalone
components, and built-in `@if`/`@for`/`@switch`. Candidate not-yet-adopted APIs to weigh: `httpResource()`
(signal-based HTTP for the store), `resource()`, `@let` in templates, `@defer`, `afterRenderEffect`
(e.g. for metric auto-sizing).
