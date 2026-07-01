# washa — Backend (Quarkus / Java)

> Cross-cutting conventions (MessageCode contract, commit style, scope discipline) → root `CLAUDE.md`.
> Frontend conventions → `src/main/angular/CLAUDE.md`.

---

## A.1 Stack and build

- **Java 25**, source/target/release all `25`. `maven-compiler-plugin` with
  `<parameters>true</parameters>` and `-g` (names + debug info retained).
- **Quarkus 3.37.0** (`quarkus-bom`). Key extensions:
  - `quarkus-amazon-lambda-http` — runs the app as an AWS Lambda behind a Function URL
  - `quarkus-resteasy` + `quarkus-resteasy-jackson` — JAX-RS with Jackson JSON
  - `quarkus-undertow` — servlet container (virtual-thread worker pool)
  - `quarkus-hibernate-orm` + `quarkus-hibernate-validator` — JPA + Bean Validation
  - `quarkus-oidc` — SSO (Google in prod, Keycloak in test via `quarkus-test-oidc-server`)
  - `quarkus-jdbc-postgresql` (prod via Neon; test via Dev Services / Testcontainers)
  - `quarkus-flyway` — DB migrations
  - `quarkus-reactive-routes` — for `@RouteFilter` SPA fallback
  - `quarkus-arc` — CDI
- **Jakarta Data API** (`jakarta.data-api` 1.0.1) — used for `CrudRepository`,
  `@Repository`, `@Query` JPQL interfaces.
- **Hibernate annotation processor** is enabled — JPA metamodel classes may be
  referenced in generated code.

### Maven lifecycle

- `./mvnw quarkus:dev` — dev mode (hot reload both Java and Angular).
- `./mvnw test` — tests with Quarkus runtime (PostgreSQL via Testcontainers Dev Services,
  Keycloak). Docker must be running.
- `./mvnw verify` — adds integration tests.
- `./mvnw package` — native image build for the Lambda (GraalVM required).

- **`./mvnw test` does NOT run the Quarkus build.** It runs surefire + the frontend tests only.
  Lambda packaging and native errors surface at `install`/`package` — run `./mvnw clean install`
  before claiming "the build passes." (`quarkus-amazon-lambda-http` needs
  `quarkus.package.jar.type=legacy-jar`; this only fails at the build/package goal, never at `test`.)
- **`-DskipFrontend=true`** skips the whole Angular build for fast backend-only iteration
  (skip-frontend profile); backend surefire still runs.
- **Run `ng` directly with the Maven-installed Node** at `target/node/node` — the system Node is
  often below Angular's `engines.node` floor: `target/node/node node_modules/@angular/cli/bin/ng.js …`.

### `application.properties` organization

Sections: HTTP, Google OIDC, Datasource, Flyway, Hibernate ORM, Application, Misc.
`%test.*` / `%dev.*` overrides in the same file. Secrets via `${ENV_VAR}` — do not hardcode.

---

## A.2 Package structure and layering

Feature packages are **vertical slices** by domain. Within each:

- **`FooEndpoint`** — `@Path`, `@Authenticated`, `@ApplicationScoped`.
  Thin: extract session user UUID, delegate to service, return `Response.ok(...)`.
  Handlers already run on a virtual thread because Undertow's worker executor is swapped for a
  `Thread.ofVirtual()` factory at deployment time — **do not add `@RunOnVirtualThread`.**
- **`FooService`** — `@ApplicationScoped @Transactional`. Mutates state, throws
  `BusinessException`, returns `*View` DTOs, never entities.
- **`FooRepository`** — `@Repository` extending `CrudRepository<T, UUID>` +
  `StatefulWriteRepository<T>`. `@Query` JPQL preferred; `default` methods for native queries.
- **`Foo`** (entity) — implements `AuditableEntity<Self>`, `Comparable<Self>`, `Serializable`.
  Nested `FooComparator` enum of named `Comparator` strategies.
- **Request records** — `@NotEmpty`, `@NotNull`, `@Size(max=255)` Bean Validation.
- **View records** — immutable DTOs returned by services. Never entities.

Cross-cutting: `common/` (entity base, repo mixin, SPA filter), `exception/` (errors).

---

## A.3 Entities

- **UUID v7 PKs.** `@UuidGenerator(style = Style.VERSION_7)` — time-ordered for B-tree locality.
- **Auditing.** Two `@MappedSuperclass`es: `AuditableEntity` (`created_at` via Hibernate
  `@CreationTimestamp`; `last_modified_at` is the temporal `@Version`, stamped on every flush) and
  `UuidEntity<T> extends AuditableEntity` (adds the VERSION_7 `uuid` `@Id`). UUID-keyed entities
  `extends UuidEntity<Self>`; natural-key entities (`AllowedIdentity`, `CurrencySetting`, `FxRate`)
  `extends AuditableEntity`. **Never set `created_at`/`last_modified_at` manually** (Hibernate owns
  them). This is washa's deliberate divergence from oppshan-files' audit-listener approach.
- **Comparators as nested enums.** `enum FooComparator implements Comparator<Foo>` with named
  strategies. `compareTo` delegates to the default strategy. Every step uses
  `Comparator.comparing(extractor, Comparator.nullsLast(Comparator.naturalOrder()))`
  (or `thenComparingLong` for primitive `long` fields, which can't be null). Field-level
  `nullsLast` is **mandatory** because JDK 25's `TreeMap.addEntryToEmptyMap` calls
  `compare(key, key)` as a sanity check on first insertion — a transient null field (e.g.,
  pre-persist `uuid`, missing JWT claim) propagates straight into a single-arg
  `Comparator.comparing` lambda and NPEs. The chain must end with `getUuid` (also wrapped in
  `nullsLast`) as a tie-breaker so `compareTo == 0` is consistent with `equals`.
- **`equals`/`hashCode`** check the entity's full identifying field set — `uuid + createdAt +
  lastModifiedAt` plus all the "what makes this Foo this Foo" fields. The audit triple guarantees
  two loads at different times are unequal; the rest of the fields keep `equals` 1:1 with the
  Comparator's field set, so `compareTo == 0 ⟺ equals` holds.
- **`@Table`** — compound `@Index` entries; `@UniqueConstraint` for business rules;
  `@CheckConstraint` (JPA 3.2 — *not* deprecated `@org.hibernate.annotations.Check`) for
  row-level invariants. Every constraint that prod enforces must be declared on the entity too —
  Hibernate `validate` mode in prod compares only column existence/types, not constraint
  names/composition, so silent drift accumulates undetected.
- **Column DEFAULTs** declared in Flyway must be mirrored on the entity via
  `@org.hibernate.annotations.ColumnDefault("…")` (no JPA standard equivalent). Otherwise
  test schema (Hibernate `drop-and-create`) lacks the default and an `INSERT` that omits
  the column will NOT-NULL-violate in test but succeed in prod.
- **Children collections.** `@OneToMany(cascade = ALL, orphanRemoval = true, fetch = LAZY)` with a `List<Foo>` (ordinal-ordered). **Lazy-init in the getter, never at the field:** declare `private List<Foo> foos;` (no `= new ArrayList<>()`) and the getter does `foos = Objects.requireNonNullElseGet(foos, ArrayList::new); return foos;`. All access goes through the getter (mappers/builders do `getFoos().add(...)`), and Hibernate tolerates a null `@OneToMany` on persist (verified by saving a month with empty collections), so **no `@PostLoad`/`@PrePersist` init is needed** — the lazy getter is sufficient. **Remove from the parent list; do not call `EntityManager.remove`.**
- **Fetch strategy.** Default `FetchType.LAZY`. Use `LEFT JOIN FETCH` in `@Query` for the
  one read that needs the children loaded.

### Entity → DTO methods

Entities expose `toFooView()` etc. Services call these; endpoints never see entities. Aggregate
or path-style reads (recursive CTEs, breadcrumb-style walks) should pass their inputs from the
caller (fetched via repo query) rather than walking a lazy proxy chain.

### Named native queries

For any aggregate or path-style query that can't be expressed as plain JPQL, use a
`@NamedNativeQuery` (via `@NamedNativeQueries`) mapped through `@SqlResultSetMapping` /
`@ConstructorResult` to a DTO. For a JPQL query that needs a constructor expression with a scalar
subquery, use `@NamedQuery` with a `SELECT new` constructor expression rather than inline JPQL in
a repository default method.

---

## A.4 Repositories: Jakarta Data + `StatefulWriteRepository`

```java
CrudRepository<Foo, UUID>, StatefulWriteRepository<Foo>
```

- **`@Query`** — JPQL in text blocks. Return `Optional<T>`, `Stream<T>`, `boolean`, or primitives.
- **`StatefulWriteRepository<T>`** — `updateWithSession`, `insertWithSession`, `deleteWithSession`,
  `attachWithSession`, `saveWithSession`. Translate JPA exceptions to Jakarta Data exceptions.
  Use instead of injecting `EntityManager` directly for write operations.
- **Default methods with `EntityManager`** — for named native queries:
  `CDI.current().select(EntityManager.class).get()`
  - Type-safe: `createNamedQuery(name, ResultClass.class)` when `@SqlResultSetMapping` maps to a class.
  - Raw: `createNamedQuery(name)` + `@SuppressWarnings("unchecked")` for bare scalar results.
- **Mixin methods must be entity-param only** (`<S extends T> S m(S e)`) with the `CDI.current()`
  lookup inlined per method. A `(Class, Object)` generic method or a `private static` helper in the
  interface makes the Hibernate Data processor fail every repository with *"repository must be
  backed by a 'StatelessSession'"*. To load a managed entity, use a `@Query … LEFT JOIN FETCH`
  finder, not a `find(Class, id)` helper.

### Convention: tenant-scope every user-scoped query

Every query on a user-owned entity must filter by the owning user's UUID. There is no row-level
security at the DB layer — this is the only enforcement.

---

## A.5 Services

### Code style (applies anywhere in `src/main/java/`)

- **Local variables are `final var`.** Combines Java 25 type inference with explicit immutability signaling at the declaration site. No bare `var`, no explicit type unless inference fails.
- **Domain-qualified variable names.** Descriptive, domain-specific names — not generic abbreviations. No single-letter variables.
- **Method ordering.** Public methods first, private helper methods grouped at the bottom of the class.
- **Multi-line parameter lists.** A constructor or method with **2+ parameters** puts the first parameter on the signature line and each subsequent parameter on its own line, aligned under the first (as `BudgetService`'s constructor and `BudgetService.savingsRate` do). Same for multi-argument call sites that wrap. Single-parameter signatures stay on one line.
- **Blank line after a block before the next statement.** A control-flow block (`if {}` / `for` / `while` / `try` / `switch`) is followed by a blank line before the next statement (as `BudgetService.savingsRate`'s guard does). Don't butt a statement directly against a block's closing brace.
- **One fluent call per line.** A builder/fluent chain of three or more calls puts each `.setX(...)` on its own line, aligned, rather than cramming several per line. (A short two-call chain may stay inline.)
- **Closed string sets are enums, in their own file.** Model a fixed set of string values (types, modes, bases, …) as a Java `enum` in its own file — never a bare `String` field switched on string literals. Constants are **UPPER_CASE** (Java convention); the lowercase wire token is a get-prefixed accessor `getValue()` annotated `@JsonValue` (+ a `@JsonCreator fromValue`) for JSON exchange, while `@Enumerated(STRING)` persists the UPPER_CASE `name()` in the relational column. The TS enum mirrors it (PascalCase constants, same lowercase values). Recreate the column's `CHECK` against the UPPER_CASE set when migrating (e.g. `GoalTargetType` → column stores `OPEN`/`AMOUNT`/`RELATIVE`, JSON carries `open`/…).

#### `Optional` usage

`Optional<T>` is a return-type signal that absence is a valid outcome. It is never a local null-check device.

| Need | Wrong | Right |
|---|---|---|
| Default a possibly-null local (eager) | `Optional.ofNullable(x).orElse("")` | `Objects.requireNonNullElse(x, "")` |
| Default a possibly-null local (lazy / expensive) | `Optional.ofNullable(x).orElseGet(() -> compute())` | `Objects.requireNonNullElseGet(x, () -> compute())` |
| Branch on null vs not-null | `Optional.ofNullable(x).map(this::handle).orElseGet(this::fallback)` | `if (x == null) { fallback(); } else { handle(x); }` or ternary |
| Consume a method that returns `Optional<T>` | — | `repository.findById(uuid).map(...).orElseThrow(...)` |

When the present-path is a single expression, chain `.map()` / `.orElse()` / `.orElseThrow()`. Use `isPresent()` + `if` only for multi-statement blocks.

### Service-layer rules

- `@ApplicationScoped @Transactional` on the class.
- **Constructor injection** with `@Inject`. All fields `private final`.
- **Bean Validation on method signatures.** `@NotNull`, `@Valid @NotNull` on returns, `@Valid` on request records.
- **`BusinessException` via static factories.** Never instantiate with a code directly; add a factory when adding a code.
- **Return views, not entities.**
- **`attachWithSession` returns the managed copy — always use the return value** (chain via `.map(fooRepository::attachWithSession)`). `EntityManager.merge()` returns the managed copy, which may differ from the passed-in instance; orphan-removal and dirty-checking only work on it.

---

## A.6 Endpoints

- All paths under `/api/...`. `@Authenticated` on class for protected endpoints.
- `@ApplicationScoped` — that is all. Handlers run on a virtual thread implicitly via the
  Undertow worker pool, so blocking JDBC calls are fine and **`@RunOnVirtualThread` must not be added**.
- **No business logic.** Extract session UUID, delegate to service, return `Response.ok(...)`.
- `@Valid` on request bodies; Bean Validation rejects bad input with 400 before service sees it.
- `POST` methods that create a resource return 201.

---

## A.7 Errors: `BusinessException` + `MessageCode`

1. Service: `throw BusinessException.somethingNotFound()` (static factory).
2. `BusinessExceptionMapper` → the HTTP status the code carries (400/401/403/404) + `{"messageCode": "messages.errors.xxx"}`.
3. `MessageCode` enum values are the i18n key paths. Jackson serializes via `@JsonValue`.

**When adding an error code: update Java `MessageCode`, TS `MessageCode`, and `en.json` together.**
Mismatches silently degrade to `MessageCode.Unknown` on the frontend.

Each `BusinessException` carries its own HTTP status, which the mapper sends: 400 for the validation and business-rule codes, 401/403 for the authentication and access-denied codes, and 404 for the not-found codes.

---

## A.8 Auth and session

- OIDC via Google in prod; Keycloak (`quarkus-test-oidc-server`) in test. The user set is closed:
  sign-in is gated by a two-user allowlist.
- **Always depend on the `UserSessionManager` interface**, not its OIDC implementation.
- Token state encrypted via `quarkus.oidc.token-state-manager.encryption-secret` (env var).
- Sign-out: `oidcSession.logout().await().indefinitely()`. Do not bypass.

### SPA routing fallback — `FrontendRoutesFilter`

`@RouteFilter(100)` reroutes all non-API, non-asset, non-OIDC requests to `/index.html`.
**Never add a backend route outside `/api/**` or `/q/**` without updating this filter.**

---

## A.9 Migrations

- Flyway-managed. Migrations in `src/main/resources/db/migration/postgresql/`.
  Tests use real PostgreSQL (Quarkus Dev Services / Testcontainers); prod is Neon.
- Hibernate `validate`-only in prod; `drop-and-create` in test via Dev Services.
- **Never edit an applied migration.** Add `V{n+1}__...sql`. See `src/main/resources/db/CLAUDE.md`.

---

## A.10 Testing

- `@QuarkusTest` + `rest-assured`. PostgreSQL via Quarkus Dev Services (Docker required).
- `quarkus-test-oidc-server` for auth. `quarkus-jacoco` for coverage.
- Test package layout mirrors `main/`. One test class per production class.
- **Prefer integration tests (`@QuarkusTest`) over mock-heavy unit tests** (washa preference — overrides the oppshan-files default). Exercise the real stack: real repositories on Dev Services Postgres, the OIDC test server / `@TestSecurity` for auth, real HTTP via `rest-assured`. Use plain JUnit only for pure, infra-free logic where an IT adds nothing — the formula evaluator, the allowlist JSON parser, money/date helpers. Aim for the 100% line-coverage target primarily through ITs.
- **Mockito: use `@Mock` / `@InjectMock` fields, not inline `mock(...)`.** Declare collaborators as fields — `@Mock` (with `@ExtendWith(MockitoExtension.class)`) for plain unit tests, `@InjectMock` from `quarkus-junit5-mockito` for CDI-resolvable beans inside `@QuarkusTest`. When the stubbed behavior varies per test, write a helper method that stubs the shared field. For a **non-CDI fixture mock inside a `@QuarkusTest`** (e.g. a `JsonWebToken` passed as a method argument, not injected), neither path fits: `@ExtendWith(MockitoExtension.class)` silently no-ops under `@QuarkusTest` (Quarkus runs the test on a different instance, so `@Mock` fields stay null) and `@InjectMock` is for beans — declare it `@Mock` and initialise with `MockitoAnnotations.openMocks(this)` in `@BeforeEach`.
- **Use BDDMockito (`given(...).willReturn(...)`), not `Mockito.when(...).thenReturn(...)`.** Pair with `BDDMockito.then(mock).should()` for verification when behavior matters. Stick to one style per file.
- **`@MockitoSettings(strictness = Strictness.LENIENT)`** on classes whose helpers stub a fixed set of claims/fields where not every test consumes every stub.
- **Test method names follow `shouldXxxYyy` (behavioral)**, not `<methodUnderTest>Verb...`. Spell out abbreviations (`IdentityProvider` not `Idp`, `JsonWebToken` not `Jwt`).
- **Seeding entities in tests:** use `QuarkusTransaction.requiringNew().run(...)`. (Audit fields are Hibernate-managed via `@CreationTimestamp` + temporal `@Version`, so don't set them manually — see A.3.) Cover the **create path** alongside the existing-entity path.
- **Test OIDC:** set `%test.quarkus.oidc.enabled=false` and synthesize identity with `@TestSecurity` + `@JwtSecurity`. A `web-app` OIDC tenant left enabled makes authenticated `@TestSecurity` requests hang (~30s read timeout).
- **The test DB is committed and shared across test classes in one run.** Use distinct keys (e.g. a unique `year_month` per test) to avoid unique-constraint collisions between classes.
- **Reuse is off, so nothing persists *across* runs.** `%test.quarkus.datasource.devservices.reuse=false` (commit `7131c98`), plus `testcontainers.reuse.enable=false` on the maintainer's machine, so the Dev Services Postgres container is recreated each run and the Testcontainers reaper (Ryuk) tears it down on JVM exit, pass or fail. That makes the *within-run* sharing above the live hazard, not cross-run accumulation: give seed-heavy tests a **unique-per-run key** (`"sub-" + UUID.randomUUID()`, a random `YearMonth`) or an **idempotent seed** (`findById(...).isEmpty()` guard) so two test classes can't collide in the shared DB. Those keys also future-proof against re-enabling reuse or two overlapping `./mvnw test` runs (which share one container via Dev Services `shared`). Compute-only tests (no persistence) are immune; CI always gets a fresh container. `scripts/test.sh` force-reaps the container after a run as belt-and-suspenders for a JVM killed before Ryuk fires.
- **JaCoCo excludes generated code:** `%test.quarkus.jacoco.excludes=**/*_.class,**/_*.class` (JPA metamodel + Jakarta Data impls), else coverage is badly understated. Compute hand-written coverage from `target/jacoco-report/jacoco.csv`.

---

## A.11 Native image (production builds)

Production deploys ship a **GraalVM-native binary** built with `./mvnw package`, targeting the
**arm64 AWS Lambda** runtime. The build packages the native artifact for Lambda deployment.

Reflection registration is the recurring trap: any DTO reached only via reflection (`*View` /
`*Request` / `*Response` records, sealed interfaces returned through JAX-RS, anything stored on a
`Principal`) needs `@RegisterForReflection`, or it fails at runtime with
`MissingReflectionRegistrationError` — which never shows up in JVM-mode tests. Register
`META-INF/services` SPI entries explicitly too.

**Native build needs ~10g heap.** The pom doesn't cap `native-image-xmx` (CI's arm64 runner
auto-sizes). On a workstation, free RAM first and pass it explicitly:
`./mvnw -Dnative package -Dquarkus.native.native-image-xmx=10g`. A lower cap fails with
GC-overhead / exit 137 — that's heap exhaustion, not a code/reflection problem (the reachability
analysis it completes first is what would surface reflection gaps).

---

## A.12 What not to do (backend) — the gotchas

The full set of conventions is documented above; these are the landmines that will silently corrupt data or NPE in prod if you trip them:

- **Never write a user-scoped query without the owning user's UUID in the WHERE clause.** There is no row-level security at the DB; this is the only tenant boundary.
- **Never use single-arg `Comparator.comparing(extractor)` for entity fields.** Pair with `Comparator.nullsLast(Comparator.naturalOrder())` (or `thenComparingLong` for primitive `long`). JDK 25's `TreeMap.addEntryToEmptyMap` calls `compare(key, key)` on first insertion; pre-persist nulls (UUID, audit fields, JWT-derived fields) NPE inside the unwrapped lambda.
- **Never ignore the return value of `attachWithSession`.** Orphan-removal and dirty-checking only work on the managed copy returned by `merge()` — chain via `.map(fooRepository::attachWithSession)`.
- **Never add `@RunOnVirtualThread`.** Undertow's worker pool is already a virtual-thread factory; the annotation is redundant and misleading.
- **Never forget `@RegisterForReflection` on reflection-only DTOs.** It passes every JVM-mode test and fails only in the native Lambda build.
