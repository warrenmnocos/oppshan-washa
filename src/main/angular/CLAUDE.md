# washa — Frontend (Angular)

> Cross-cutting conventions (MessageCode contract, commit style, scope discipline) → root `CLAUDE.md`.
> Backend conventions → `src/main/java/CLAUDE.md`.

---

## B.1 Directory layout

```
src/main/angular/
├── public/
│   └── i18n/en.json              # translation strings, keyed by messageCode paths
└── src/
    ├── index.html
    ├── main.ts
    ├── styles.scss               # global styles incl. .dialog-*, .skeleton-*
    └── app/
        ├── app.config.ts         # root providers, listener registration
        ├── app.routes.ts
        ├── components/           # reusable presentation components
        ├── pages/                # routable pages (dashboard, budget, sign-in, sign-out)
        ├── listeners/            # MESSAGE_LISTENERS: react to ApplicationEvents
        ├── services/             # HTTP + bus + cross-cutting state
        ├── models/               # DTOs, enums, event/command payload interfaces
        └── misc/                 # pipes, guards, small utilities
```

File naming: kebab-case file, PascalCase class. Component triad: `.ts`, `.html`, `.scss`.

---

## B.2 Core architecture: the event bus

### B.2.1 The bus primitives

- **`MessageBusService`** — owns a `Subject<ApplicationEvent>`. Exposes:
  - `applicationEventStream: Observable<ApplicationEvent>`
  - `applicationEventSignal: Signal<ApplicationEvent>`
  - `applicationEvenTypeSignal: Signal<ApplicationEventType>` (*sic* — missing `t`, preserved everywhere)
  - `fireApplicationEvent(event)` / `fireApplicationEventOfType(type)`

- **`ApplicationEvent`** — universal envelope: `constructor(type, payload: unknown = null)`.
  Only one class. Everything else is a plain `interface` in the payload.

- **`ApplicationEventType`** — single flat enum of all events. Lifecycle suffixes:
  - `*Initiated` — user asked for it
  - `*Confirmed` / `*Cancelled` — user resolved a dialog
  - `*Succeeded` / `*Failed` — domain outcome
  - `*Shown` / `*Hidden` — pure UI state

### B.2.2 Listeners — the `MESSAGE_LISTENERS` pattern

- Extend `AbstractApplicationEventListener` (filter by type, call `onApplicationEvent`).
- Registered in `app.config.ts` as multi-providers of `MESSAGE_LISTENERS` token.
- **Never subscribe to the bus inside a listener.** `MessageReactorService` fans out.
- Naming: `<Domain><Action>ApplicationEventListener`.

### B.2.3 CQRS split

- **Commands (mutations) → bus via listeners.** Fire `*Confirmed` with a command payload;
  the listener calls the service and emits `*Succeeded` / `*Failed`.
- **Queries (reads) → direct service calls.** Inject the domain service, call in `ngOnInit`/`computed`.
- **Listener purity rule:** A listener that makes an HTTP call must not also mutate service state directly. Split responsibilities: one listener for the HTTP call (fires outcome events), a dedicated listener for the state effect.

### B.2.4 Typed payload design

`payload` is `unknown`. Types come from per-event interfaces, not subclasses.

- **Command interfaces** live in `models/*-commands.ts` (imperative names).
- **Outcome / lifecycle-payload interfaces** live in `models/*-events.ts` (bare past tense, no suffix). `*Shown` / `*Hidden` UI-state payloads belong here too.
- Listeners cast once: `event.payload as SomethingHappened`.
- **Self-sufficient payloads**: include every field a listener needs. No reaching back to services.
- Do not introduce generics on `ApplicationEvent`. The single-class envelope is deliberate.

### B.2.5 When to use the bus vs other patterns

- `input()` / `output()` — for structural parent→child props and child→parent events.
- Bus — for cross-cutting effects: a toolbar click triggers a sibling dialog; an HTTP success drives a refresh + notification.
- Direct service calls — for reads.

---

## B.3 Page/component conventions

- **Pages** (`pages/*`) — lazy-loaded via `loadComponent` in `app.routes.ts`.
- **Components** (`components/*`) — reusable, not route-mounted.
- **Standalone: true** everywhere. No NgModules.
- **Signals over RxJS.** `signal`, `input`, `input.required`, `computed`, `toSignal` for state.
  RxJS at the edges (HTTP, bus Subject, route.url). Tear down subscriptions in `ngOnDestroy`.
- **Control-flow syntax.** Use `@if`, `@for`, `@switch` — not `*ngIf` / `*ngFor`.
- **Signal-based inputs/outputs.** Use `input()` / `input.required()` / `output()` — not `@Input()` / `@Output()`.

### Dialog pattern

Dialogs are siblings in the tree, mounted via an `@if` gate on `applicationEvenTypeSignal()`:

```html
@if (messageBusService.applicationEvenTypeSignal() === ApplicationEventType.SomethingInitiated) {
  <app-something-dialog/>
}
```

Dialog flow:
1. Trigger fires `*Initiated` with context payload.
2. `@if` gate mounts dialog.
3. Dialog reads `computed(() => bus.applicationEventSignal().payload as SomeView)`.
4. Confirm → fires `*Confirmed` (command payload). Cancel → fires `*Cancelled`.
5. Any other event collapses the `@if`, unmounting the dialog.

### Error display

- **`ErrorState`** — full-panel; used when navigation/load fails. Replaces main content.
- **`NotificationCenter`** — fixed panel rendering toast messages (and any progress sections). Add `<app-notification-center/>` once to the page template; it self-hides when there are no notifications.

---

## B.4 Services

- **`AuthService`** — SSO login/logout. Exposes `getCurrentUser()` returning an Observable of the current user view.
- **Domain HTTP services** — pure HTTP wrappers. Return Observables. No bus knowledge. All paths under `/api/...`.
- **`NotificationService`** — root-scoped signal store of active notifications. `MessageNotification` (toast, auto-dismiss after `NotificationDurationMs`). Call `push(messageCode, params?)` for toasts.
- **`MessageBusService`** / **`MessageReactorService`** — bus primitives.
- **`JsonMapper`** — wraps `class-transformer`'s `plainToInstance`.

Services: `@Injectable({providedIn: 'root'})`. Listeners: bare `@Injectable()` + explicit
`MESSAGE_LISTENERS` multi-provider entry in `app.config.ts` — **easy to forget the provider entry**.

### Initialization in the constructor body

**All class-member initialization happens in the constructor body**, not at the field declaration. This
applies uniformly: services (DI), signals, computed values, `toSignal` bridges, RxJS `Subject`s and
derived `Observable`s. Field declarations carry only the type; the constructor performs the assignment.

```typescript
// CORRECT
export class NotificationCenter {
  protected readonly collapsed: WritableSignal<boolean>;

  constructor(private readonly notificationService: NotificationService) {
    this.collapsed = signal(false);
  }
}

// WRONG — field-level initializers
export class NotificationCenter {
  protected readonly collapsed = signal(false);
  constructor(private readonly notificationService: NotificationService) {}
}
```

**The single exception — `input()` / `input.required()` / `output()` must stay at field level.** The
Angular AOT compiler analyzes these declarations statically; calling them from the constructor produces
**NG8108** (input) or **NG8109** (output).

**Avoid `model()` entirely.** Like `input()`/`output()`, `model()` cannot live outside a field
initializer (**NG8110**) — and it generates a public input/output pair regardless of whether any parent
two-way binds. For internal writable state use `signal()` + the constructor; for template two-way binding
to a signal, expand the binding manually:

```html
<!-- INSTEAD OF [(ngModel)]="myField" with model() -->
<input [ngModel]="myField()" (ngModelChange)="myField.set($event)" />
```

Reserve `model()` only when a child component genuinely exposes two-way binding to its parent.

**Other exceptions.** `inject()` is the only DI mechanism inside functional guards and interceptors
(no class context) — use it in the function body. Inside classes, use constructor parameters with
`private readonly`; do not declare class fields with `= inject(SomeService)`. Token-based
multi-providers use the `@Inject(TOKEN)` decorator on a constructor parameter.

---

## B.5 Models and i18n

### `MessageCode` (`models/message-code.ts`)

Single enum; values are i18n key paths. Severity derived from prefix:
`messages.errors.*` = Error, `messages.warning.*` = Warning, else Info.

**Must stay in sync with Java `MessageCode` enum and `en.json`.** See root CLAUDE.md § C.1.

### `en.json`

Sections: `messages.errors.*`, `messages.info.*`, `messages.warning.*`, plus per-feature UI keys.

When adding a message: add `MessageCode` entry + `en.json` key + Java `MessageCode` (if backend emits it), all in one change.

### Views (DTOs)

Use `class-transformer` `@Type(() => X)` for nested hydration. Treat as immutable. Field names match
Java `*View` records 1:1 — update both sides together.

---

## B.6 Styling conventions

- **Units: `rem` (base 16px).** All spacing, sizing, font-size, border-radius, box-shadow,
  and media query breakpoints use `rem`. The only exception: `1px` on `border` and `outline`
  properties (hairline rendering). Do not use `px` for new code. Do not use `em`.
- **`styles.scss`** — `.dialog-*` classes, `.skeleton-*` scaffolding (shimmer + per-context sizing), reset.
- **Per-component SCSS** — local sizing/states only. Don't redefine globals; don't put skeleton
  sizing here either.
- **Skeleton loading** — `.skeleton-line` provides the shimmer; sizing is **always global**, never
  per-component. Two patterns:
    - **Toggling spans** — use `[class.skeleton-line]="!data()"` and rely on a global compound selector for dimensions.
    - **Placeholder blocks** — compose two classes: `class="skeleton-line skeleton-X"`, where `skeleton-X` is a global size-only class. Add new size classes to `styles.scss` as needed.
  Empty spans collapse to zero, so sizing is mandatory — but it lives in `styles.scss`, not in the component's SCSS.

### SVG icons (`public/icons/`)

Use SVG assets for iconography. Never use Unicode symbols (▶ ✕ →) as icons.

---

## B.7 Common pitfalls and gotchas

- **`ActivatedRoute.url` replays on subscribe.** Guard with `if (path === this.currentPath) return`.
  Subscribe once; do not re-subscribe after navigation.
- **Dialog unmounts on any bus event.** Intra-dialog operations must not fire events that
  change `applicationEvenTypeSignal`.
- **`applicationEvenType` typo** (missing `t`) is everywhere. Do not rename in isolation.
- **Listener registration.** New listener = new class + new `MESSAGE_LISTENERS` multi-provider
  entry in `app.config.ts`.
- **HTTP interceptor must pass all event types.** Never `filter` the observable to
  `HttpResponse` only — that kills `HttpSentEvent`, `HttpHeaderResponse`, and progress events.
  Handle errors via `catchError`; let the full event stream through.
- **Event manipulation in the component method, not the template.** `$event.stopPropagation()`
  and `$event.preventDefault()` belong in the handler method body, not chained in template expressions.

---

## B.8 Adding a new feature: the recipe

1. `ApplicationEventType` — add `*Initiated`, `*Confirmed`, `*Cancelled`, `*Succeeded`, `*Failed`.
2. `models/*-commands.ts` — command interface.
3. `models/*-events.ts` — success + failure outcome interfaces (bare past tense, no suffix).
4. `MessageCode` (TS + Java) + `en.json` — info code for success, error codes for failures.
5. Domain HTTP service (or new service) — HTTP method.
6. Listener — calls service, emits outcomes (events only — no direct service mutations).
7. `app.config.ts` — register listener in `MESSAGE_LISTENERS`.
8. Dialog component — reads payload from `applicationEventSignal()`, fires Confirmed/Cancelled.
9. Parent page `@if` gate — mounts dialog when `applicationEvenTypeSignal() === *Initiated`.
10. Trigger — button/menu fires `*Initiated`.
11. For features with client-side constraints, validate in the component **before** firing the `*Confirmed` command. Fire `*Failed` events from the component for rejected items; only valid inputs enter the command payload.

Notification on success/failure is free — the existing notification listener reacts to any
outcome payload with `{messageCode}`.

---

## B.9 What not to do (frontend) — the gotchas

The full set of conventions is documented above; these are the landmines that will silently break the app or churn unrelated code:

- **Never fix the `applicationEvenType` typo (missing `t`) in isolation.** It's a single name used everywhere — a partial rename desyncs every listener and bus reference. If you ever rename it, do every occurrence in one commit.
- **Never call a domain service for mutations from components.** Mutations go through the bus (fire `*Confirmed`, listener calls the service). Direct calls bypass CQRS and break the listener pipeline that emits `*Succeeded` / `*Failed` toasts.
- **Never subscribe to `ActivatedRoute.url` more than once.** It replays on subscribe; an unguarded second subscription re-fires every navigation and re-runs the load handler. Guard with `if (path === this.currentPath) return`.
- **Don't assert translated text in tests.** With no i18n JSON loaded, the `translate` pipe returns the raw key. Assert on event types, payloads, signals, and DOM structure (or the key itself) instead.

---

## B.10 Unit tests

The app runs `*.spec.ts` through the `@angular/build:unit-test` builder (Vitest + jsdom). Run with `yarn test` or `npx ng test --no-watch`. They also run automatically in the Maven `test` phase: the `frontend-maven-plugin` `yarn-test` execution in `pom.xml` runs `yarn test --no-watch`, so `./mvnw test` exercises both stacks and `-DskipTests` skips both. Specs are co-located next to the source they cover.

- **Test names follow the backend `shouldXxx` convention:** `it('should ...')`, matching the Java suite.
- **Vitest globals are configured** (`tsconfig.spec.json` → `types: ["vitest/globals"]`). Do **not** import `describe` / `it` / `expect` / `vi` / `beforeEach` — use them directly.
- **Pick the lightest harness that works:**
  - Pure functions, pipes without DI, and listeners → construct directly with `new`, casting mock collaborators as `mock as unknown as RealType`. Listeners are driven via `listener.onMessage(new ApplicationEvent(type, payload))`.
  - Root services, components, and pages → `TestBed`. Add only the providers the unit needs: `provideTranslateService({lang: 'en'})`, `provideHttpClient()` + `provideHttpClientTesting()`, `provideRouter([])`. Root services resolve automatically; grab them with `TestBed.inject(...)` and `vi.spyOn(bus, 'fireApplicationEvent')` to assert fired events.
- **HTTP:** `HttpTestingController` with `provideHttpClientTesting()`; `expectOne(url)`, assert method/body, `req.flush(json)`.
- **`class-transformer` view hydration:** feed `plainToInstance(View, {...})` a plain object (strings, never live `DateTime`s — that crashes luxon's deep-clone) and assert nested `@Type` classes.
- **Timers:** `vi.useFakeTimers()` + `vi.advanceTimersByTime(NotificationDurationMs)` for the toast auto-dismiss.
- **Required inputs:** `fixture.componentRef.setInput('name', value)` **before** `fixture.detectChanges()`.
- **Dialog context:** fire the matching `*Initiated` / `*Shown` event on the bus **before** `createComponent` so `applicationEventSignal()` carries the payload (the `toSignal` bridge updates synchronously on emit).
- **Coverage** needs the `@vitest/coverage-v8` dev dep. Run with the Maven-installed Node (the system Node is often below Angular's `engines.node` floor, so `ng` won't start): `target/node/node node_modules/@angular/cli/bin/ng.js test --no-watch --coverage`.
