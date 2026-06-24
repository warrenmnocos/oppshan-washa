# washa — Frontend (Angular)

> Cross-cutting conventions (MessageCode contract, commit style, scope discipline) → root `CLAUDE.md`.
> Backend conventions → `src/main/java/CLAUDE.md`.

> **Architecture in one line:** Angular 22 standalone + **zoneless**, state held in **signals** and a
> **signal store** (`BudgetStore`), reads/writes through a thin HTTP service, access gated by
> **functional guards**. There is no event bus, no CQRS/listeners, no `class-transformer` — washa
> deliberately diverged from oppshan-files; this doc describes what is actually built.

---

## B.1 Directory layout

```
src/main/angular/
├── public/
│   ├── i18n/en.json             # translation strings, keyed by messageCode paths + per-feature UI keys
│   └── icons/                   # SVG icon assets
└── src/
    ├── index.html
    ├── main.ts
    ├── styles.scss              # global design tokens + cards/rows/metrics/modals/.skel/.editrow/.ratestep
    └── app/
        ├── app.config.ts        # root providers (zoneless, http+fetch, router, ngx-translate)
        ├── app.routes.ts        # AppShell parent route wraps lazy children; sign-in standalone
        ├── app.ts               # root component (<router-outlet/>)
        ├── components/          # shared chrome: app-shell, app-header, app-footer
        ├── pages/               # routable pages: dashboard, sign-in, budget (+ its dialogs, money-chart)
        ├── services/            # signal store, HTTP service, functional guards, pipes
        └── models/              # plain DTO interfaces + the MessageCode enum
```

File naming: kebab-case file, PascalCase class. Component triad: `.ts`, `.html`, `.scss`.

---

## B.2 Core architecture: signals + a signal store

- **Zoneless** (`provideZonelessChangeDetection()`), standalone components, no NgModules.
- **State lives in signals.** The budget app's state is owned by **`BudgetStore`**
  (`@Injectable({providedIn:'root'})`): private writable `signal`s exposed as readonly, `computed()`
  for derived values, and a **`mutate(fn)`** that deep-clones the working month, applies `fn`, marks
  dirty, and triggers a **debounced** recompute. `load()` / `save()` / `navigate()` drive month CRUD.
  Pages inject the store and call its methods; they don't hold domain state themselves.
- **The backend is the source of truth for computed figures.** The store POSTs the working month to
  `/api/budget/compute` (debounced ~250ms) and to `/api/budget/month/{key}` to save. **Don't recompute
  money math on the client** — render `store.computed()`.
- **Reads/effects** go through **`BudgetApiService`**, a thin HTTP wrapper returning `Observable`s; the
  store subscribes. Access is gated by **functional guards** (`authGuard`/`guestGuard`) that call
  `/api/me` (401 → sign-in, 403 → not-allowlisted, 200 → in).
- **Parent ↔ child** uses `input()` / `input.required()` / `output()`.

---

## B.3 Page/component conventions

- **Pages** (`pages/*`) — lazy-loaded via `loadComponent` in `app.routes.ts`. Signed-in pages are
  children of the `AppShell` parent route (fixed header/footer, scrolling content); sign-in is standalone.
- **Components** (`components/*`) — reusable, not route-mounted.
- **Standalone: true** everywhere. No NgModules.
- **Signals over RxJS.** `signal`, `computed`, `linkedSignal`, `input`, `input.required`, `toSignal`.
  RxJS only at the edges (HTTP). **Control flow:** `@if` / `@for` / `@switch`, not `*ngIf` / `*ngFor`.
- **Signal inputs/outputs:** `input()` / `input.required()` / `output()`, not `@Input()` / `@Output()`.

### Dialog pattern (signal-based)

The page owns an editing-index signal; the dialog is mounted by `@if` and edits a **clone** that is
only committed on save. Established across the salary/goal/debt edit dialogs:

```typescript
// page
readonly editingSalaryIndex = signal<number | null>(null);
editSalary(i: number) { this.editingSalaryIndex.set(i); }
editedSalary(): Salary | null { const i = this.editingSalaryIndex(); return i === null ? null : this.month().salaries[i] ?? null; }
applySalary(s: Salary) { const i = this.editingSalaryIndex(); if (i !== null) this.store.mutate(m => m.salaries[i] = s); this.editingSalaryIndex.set(null); }
```
```html
@if (editedSalary(); as salary) {
  <app-salary-dialog [salary]="salary" [currencies]="month().cur"
                     (saved)="applySalary($event)" (cancelled)="closeSalaryDialog()"/>
}
```
Dialog internals: `input.required` entity → a `linkedSignal` **deep-clone draft** → mutate via a
`patch()` helper (`structuredClone(draft())`, change, `.set()`) → **Save** emits `saved(draft)`,
**Cancel** emits `cancelled()`. Edits never touch the store until Save, so Cancel discards cleanly.
Reuse the `.modalwrap`/`.modalcard`/`.fld`/`.editrow`/`.bkt-*`/`.ratestep` styles in `styles.scss`.

### Error display

Inline, via a local `signal<string | null>` rendered in the template (e.g. the budget page's
`importError`). There is **no** global toast/notification center or full-panel error component — don't
reference one; add an inline signal where a page needs to surface an error.

---

## B.4 Services, DI, and initialization

- **`BudgetApiService`** — pure HTTP wrapper, returns `Observable`s, all paths under `/api/...`.
- **`BudgetStore`** — the signal store (see B.2). Root-scoped.
- **`MoneyPipe`** (`services/money.pipe.ts`) — formats an amount + currency for display.
- **`auth.guard.ts`** — functional `authGuard` / `guestGuard`.
- Injectable services use `@Injectable({providedIn: 'root'})`.

**DI and signal init are field-level** (washa's convention — it does *not* use a constructor-body
init pattern):

```typescript
export class BudgetPage {
  private readonly api = inject(BudgetApiService);   // field-level inject()
  readonly store = inject(BudgetStore);
  readonly fxRates = signal<Record<string, number>>({});   // field-level signal init
}
```
Functional guards/interceptors (no class context) call `inject()` in the function body.

**`input()` / `input.required()` / `output()` must stay at field level** — the AOT compiler analyzes
them statically; calling them from a constructor produces **NG8108** (input) / **NG8109** (output).
**Avoid `model()`** (**NG8110**, and it forces a public two-way pair); for template two-way binding use
`[value]` + `(input)`/`(change)` (as the dialogs do) or `[ngModel]` + `(ngModelChange)`.

---

## B.5 Models and i18n

### `MessageCode` (`models/message-code.ts`)

Single enum; values are i18n key paths. Severity from prefix: `messages.errors.*` = Error,
`messages.warning.*` = Warning, else Info. **Must stay in sync with Java `MessageCode` and `en.json`** —
see root CLAUDE.md § C.1 (TS is a superset; Java is the backend-emitted subset).

### `en.json`

Sections: `messages.errors.*`, `messages.info.*`, `messages.warning.*`, plus per-feature UI keys
(`dashboard.*`, `signIn.*`, `header.*`, `footer.*`). **UI strings go through the `translate` pipe.**
Adding a message: `MessageCode` entry + `en.json` key + Java `MessageCode` (if the backend emits it),
all in one change.

### Views (DTOs)

**Plain TS interfaces** in `models/budget.models.ts`. Field names match the Java `*View` records 1:1
(JSON names `amt` / `cur` / `var` / `wd` / `afterYears` / `sym`) — update both sides together. No
`class-transformer`/hydration; they're data.

---

## B.6 Styling conventions

- **Units: `rem` (base 16px).** Spacing, sizing, font-size, radius, shadow, and breakpoints all in
  `rem`. Only exception: `1px` on `border`/`outline`. No `px` for new code, no `em`.
- **`styles.scss`** — the design-token `:root` (light + a `prefers-color-scheme: dark` block) drives the
  whole UI; re-theme by editing tokens, not component colors (washa = files' design language in an amber
  palette). Also holds the shared component classes (`.card`, `.row`, `.metric`, `.modalwrap`,
  `.editrow`, `.ratestep`, `.bkt-*`) and the **shimmer skeleton** classes `.skel` / `.skeltext`
  (`@keyframes shimmer`, sized globally).
- **Per-component SCSS** — local layout/states only; don't redefine globals.
- **Mobile-first** at the `37.5rem` breakpoint with `pointer: coarse` touch sizing.

### SVG icons (`public/icons/`)

Use SVG assets for iconography. Never use Unicode symbols (▶ ✕ →) as icons.

---

## B.7 Common pitfalls and gotchas

- **`mutate`/`patch` operate on clones.** Returning the same object reference won't trigger signal
  updates — always `structuredClone`, change, then `.set()` a new object.
- **The compute round-trip is debounced (~250ms).** In tests, flush the `/api/budget/compute` request
  (and the month-load + fx requests the budget page fires on mount).
- **Don't assert translated text.** With no i18n JSON loaded, the `translate` pipe returns the raw key —
  assert on signals, DOM structure, or the key itself.
- **Event manipulation in the handler method, not the template.** `$event.stopPropagation()` /
  `preventDefault()` go in the method body, not chained in template expressions.

---

## B.8 Adding a new feature: the recipe

1. **Model** — add/extend the interface in `models/budget.models.ts` (match the Java `*View`).
2. **Backend** — make sure the `*View`, entity, and `compute()` support it (backend `CLAUDE.md`).
3. **Store** — add a `mutate`-based method if the page needs one (the backend recomputes on the next
   debounced compute; don't compute money math locally).
4. **Dialog** (for editing a complex entity) — a component with an `input.required` entity, a
   `linkedSignal` draft, a `patch()` helper, and `saved`/`cancelled` outputs; reuse the modal styles.
5. **Page** — `editingXIndex` signal + `editX()` / `editedX()` / `applyX()` / `closeXDialog()`; mount
   with `@if`; apply the emitted entity via `store.mutate`.
6. **i18n** — any new `MessageCode` (TS + Java if backend-emitted) + `en.json` keys.
7. **Spec** — `TestBed` with only the providers the unit needs; `setInput` before `detectChanges`;
   flush HTTP.

---

## B.9 What not to do (frontend) — the gotchas

- **Don't recompute money math on the client.** POST the working month to `/api/budget/compute`; the
  backend is authoritative for money-out, savings rate, projections, etc.
- **Don't mutate store state from inside a dialog.** Edit the `linkedSignal` clone, emit `saved`, and let
  the page apply it via `store.mutate` — so Cancel always discards.
- **Don't reintroduce the event bus / CQRS listeners / `class-transformer` / constructor-body init.**
  washa is signals + a signal store + field-level `inject()`/signal init.
- **Don't assert translated text in tests** (the pipe echoes the key with no JSON loaded).
- **No `px`** (rem only) and **no Unicode icon glyphs** (SVG assets only).

---

## B.10 Unit tests

`*.spec.ts` run through the `@angular/build:unit-test` builder (Vitest + jsdom). They run in the Maven
`test` phase (`frontend-maven-plugin` `yarn-test` in `pom.xml`), so `./mvnw test` exercises both stacks
and `-DskipTests` skips both. Specs are co-located next to their source.

- **Test names follow the backend `shouldXxx` convention:** `it('should …')`.
- **Vitest globals are configured** (`tsconfig.spec.json` → `types: ["vitest/globals"]`) — use
  `describe`/`it`/`expect`/`vi`/`beforeEach` directly, don't import them.
- **Harness:** `TestBed` with only the providers the unit needs — `provideTranslateService({lang: 'en'})`,
  `provideHttpClient()` + `provideHttpClientTesting()`, `provideRouter([])`. Root services
  (`BudgetStore`) resolve automatically; grab them with `TestBed.inject(...)`.
- **HTTP:** `HttpTestingController` + `provideHttpClientTesting()`; `expectOne(url)`, assert method/body,
  `req.flush(json)`. The budget page on mount fires month-load + `/api/budget/compute` + fx — flush all
  three before asserting.
- **Required inputs:** `fixture.componentRef.setInput('name', value)` **before** `fixture.detectChanges()`
  (this is how the dialog specs feed the entity + currencies).
- **Don't assert translated text** — assert keys, DOM structure, or component state.
- **Coverage** needs `@vitest/coverage-v8`. Run with the Maven-installed Node (system Node is often below
  Angular's `engines.node` floor): `target/node/node node_modules/@angular/cli/bin/ng.js test --no-watch --coverage`.
