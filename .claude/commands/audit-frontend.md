Audit the frontend Angular code at `src/main/angular/src/` and report problems grouped by severity.

This audit is heavy on file reads; **dispatch a `general-purpose` agent** to do it rather than running it in the main session.

## Required reading first (the agent does this)

1. `CLAUDE.md` (root) — § C.1 (MessageCode contract), § C.2 (commit conventions), § C.3 (scope).
2. `src/main/angular/CLAUDE.md` — frontend-specific conventions (event bus + listener pattern, CQRS split, signal-first state, constructor-body initialization, `models/` naming, styling rules with `rem`, common pitfalls).
3. The project memory index at `.claude/memory/MEMORY.md` (relative to the repo root) and any `feedback_*.md` files it references. These document established conventions; flag deviations as findings.

## Scope

Sweep every directory under `src/main/angular/src/app/`: `pages/`, `components/`, `listeners/`, `services/`, `models/`, `misc/`. Also scan `app.config.ts`, `app.routes.ts`, `styles.scss`, `public/i18n/en.json`.

## Look for, in priority order

**Architecture & bus discipline**
- Components calling a domain service mutation directly (must route through the bus via `*Initiated` → listener → service)
- Listeners that both make HTTP calls **and** mutate service state directly (split: HTTP listener fires outcome events; a separate listener consumes outcomes for state effects)
- Listeners subscribing to the bus internally (must use `AbstractApplicationEventListener` + `MESSAGE_LISTENERS` registration; `MessageReactorService` fans out)
- New listener class without a corresponding `MESSAGE_LISTENERS` multi-provider entry in `app.config.ts`
- `ApplicationEvent` subclassed instead of using the single envelope + payload `interface`
- Outcome interfaces named with `XxxEvent` / `XxxPayload` suffix (must be bare past tense)
- Payloads that aren't self-sufficient (listener has to call back into a service to reconstruct context)
- Generics introduced on `ApplicationEvent`
- `applicationEvenType` typo "fixed" in isolation (must stay typo'd everywhere)

**State, signals, lifecycle**
- Field-level initializers for signals / computed / `toSignal` / RxJS subjects (must be in constructor body) — exception: `input()` / `input.required()` / `output()` stay at field level
- `model()` usage anywhere (forbidden — expand `[(ngModel)]` manually instead)
- Class fields declared as `= inject(SomeService)` inside class context (use constructor parameter `private readonly`)
- `@Input()` / `@Output()` decorators (must be `input()` / `output()`)
- RxJS subscriptions without teardown in `ngOnDestroy`
- `ActivatedRoute.url` subscribed without same-path replay guard
- `*ngIf` / `*ngFor` (must be `@if` / `@for` / `@switch`)
- NgModules (everything is `standalone: true`)

**HTTP & interceptors**
- HTTP interceptor `filter`-ing the observable to `HttpResponse` only (kills `HttpSentEvent`, header response, progress events)
- A pure HTTP service containing bus knowledge (must stay a pure HTTP wrapper)

**Templates**
- `$event.stopPropagation()` / `$event.preventDefault()` chained in template expressions (must be in the handler method body)
- `(click)="doThing(); $event.stopPropagation()"` style chains
- String literal union types in templates / models (prefer TS enums)
- Severity computed from event payload instead of derived from `MessageCode` prefix at point of use

**i18n & MessageCode**
- TS `MessageCode` enum entries missing from `en.json` (silent empty-string render)
- Java-emitted error codes missing from TS enum (silent `MessageCode.Unknown` degrade)
- Run `/sync-messagecode` mentally against the diff if the audit touches messages
- Dot-segmented filenames in `models/` (e.g., `foo.bar.ts` — must be `foo-bar.ts`)

**Styling**
- `px` units in new SCSS (must be `rem`); only `1px` on `border` / `outline` is allowed
- `em` units anywhere
- Skeleton sizing in component-local SCSS (must be in global `styles.scss` via `.skeleton-X` size classes)
- Unicode symbols (▶ ✕ →) used as icons (must be SVG assets)

**Notifications**
- `window.alert` usage (must use `NotificationService.push`)
- Components calling `NotificationService` directly for command outcomes (the existing notification listener handles `{messageCode}` payloads automatically)

**Code quality**
- Dead code, unused imports
- Half-finished implementations / TODOs / FIXMEs
- Comments that describe WHAT (only WHY belongs)
- Unawaited `Router.navigate` promises that block on outcome (known rough edge per § C.3 — note as pre-existing, don't sweep)

## Output format

Group findings by severity (**Critical / High / Medium / Low**). For each:
- One-line summary
- File path and line range (`path:line` format)
- Why it's a problem (which convention or which user-feedback rule)
- Suggested fix in one sentence

Cap at ~2000 words. Skip stylistic-only or pedantic findings — focus on things that actually matter. If a category turns up nothing, say so in one line. End with a "Categories with no findings" list to make absence-of-evidence explicit.

**Read-only audit. Do not modify any files.**
