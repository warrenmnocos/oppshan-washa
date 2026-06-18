# washa — Backend (Quarkus / Java)

> Cross-cutting conventions (MessageCode contract, commit style, scope discipline) → root `CLAUDE.md`.
> Frontend conventions → `src/main/angular/CLAUDE.md`.

---

## A.1 Stack and build

- **Java 25**, source/target/release all `25`. `maven-compiler-plugin` with
  `<parameters>true</parameters>` and `-g` (names + debug info retained).
- **Quarkus 3.36.1** (`quarkus-bom`). Key extensions:
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
- **Auditing.** `AuditableEntity<Self>` + `@EntityListeners({AuditableEntityEntityListener.class})`.
  Listener sets `createdAt` on `@PrePersist`, bumps `lastModifiedAt` on `@PrePersist` + `@PreUpdate`.
  **Never set these manually.**
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
- **Children collections.** `@OneToMany(cascade = ALL, orphanRemoval = true)` with
  `SortedSet<@NotNull Foo>` backed by `TreeSet`. Initialize lazily via
  `requireNonNullElseGet(x, TreeSet::new)` in `@PostLoad` and `@PrePersist`.
  **Remove from the parent set; do not call `EntityManager.remove`.**
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

### Convention: tenant-scope every user-scoped query

Every query on a user-owned entity must filter by the owning user's UUID. There is no row-level
security at the DB layer — this is the only enforcement.

---

## A.5 Services

### Code style (applies anywhere in `src/main/java/`)

- **Local variables are `final var`.** Combines Java 25 type inference with explicit immutability signaling at the declaration site. No bare `var`, no explicit type unless inference fails.
- **Domain-qualified variable names.** Descriptive, domain-specific names — not generic abbreviations. No single-letter variables.
- **Method ordering.** Public methods first, private helper methods grouped at the bottom of the class.

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
2. `BusinessExceptionMapper` → `400 Bad Request` + `{"messageCode": "messages.errors.xxx"}`.
3. `MessageCode` enum values are the i18n key paths. Jackson serializes via `@JsonValue`.

**When adding an error code: update Java `MessageCode`, TS `MessageCode`, and `en.json` together.**
Mismatches silently degrade to `MessageCode.Unknown` on the frontend.

All `BusinessException`s map to HTTP 400. Extend the mapper for other semantics (403, 404).

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
- **Prefer plain unit tests over `@QuarkusTest` / `@QuarkusIntegrationTest`.** Reach for the Quarkus runtime only when the test actually needs a real database, real CDI, real HTTP, or a real OIDC provider — e.g., entity-tree tests that exercise recursive CTEs or cascade-flush behavior, endpoint tests via `rest-assured`, and the Flyway/OIDC integration tests. Pure logic (comparator chains, view-builder helpers) belongs in plain JUnit + Mockito. Booting Quarkus for a test that doesn't need it costs ~5–8 s of dev-services startup and obscures the dependency surface.
- **Mockito: use `@Mock` / `@InjectMock` fields, not inline `mock(...)`.** Declare collaborators as fields — `@Mock` (with `@ExtendWith(MockitoExtension.class)`) for plain unit tests, `@InjectMock` from `quarkus-junit5-mockito` for CDI-resolvable beans inside `@QuarkusTest`. When the stubbed behavior varies per test, write a helper method that stubs the shared field.
- **Use BDDMockito (`given(...).willReturn(...)`), not `Mockito.when(...).thenReturn(...)`.** Pair with `BDDMockito.then(mock).should()` for verification when behavior matters. Stick to one style per file.
- **`@MockitoSettings(strictness = Strictness.LENIENT)`** on classes whose helpers stub a fixed set of claims/fields where not every test consumes every stub.
- **Test method names follow `shouldXxxYyy` (behavioral)**, not `<methodUnderTest>Verb...`. Spell out abbreviations (`IdentityProvider` not `Idp`, `JsonWebToken` not `Jwt`).
- **Seeding entities in tests:** use `QuarkusTransaction.requiringNew().run(...)` and **pre-set audit fields manually** (`setCreatedAt(seedInstant).setLastModifiedAt(seedInstant)`) before adding to a `SortedSet` or `TreeMap`. `@TestTransaction` is unreliable for entities with `SortedSet` children because cascade-flushing triggers the JDK 25 `TreeMap.compare(key, key)` sanity check that needs non-null compare keys. Cover the **create path** alongside the existing-entity path.

---

## A.11 Native image (production builds)

Production deploys ship a **GraalVM-native binary** built with `./mvnw package`, targeting the
**arm64 AWS Lambda** runtime. The build packages the native artifact for Lambda deployment.

Reflection registration is the recurring trap: any DTO reached only via reflection (`*View` /
`*Request` / `*Response` records, sealed interfaces returned through JAX-RS, anything stored on a
`Principal`) needs `@RegisterForReflection`, or it fails at runtime with
`MissingReflectionRegistrationError` — which never shows up in JVM-mode tests. Register
`META-INF/services` SPI entries explicitly too.

---

## A.12 What not to do (backend) — the gotchas

The full set of conventions is documented above; these are the landmines that will silently corrupt data or NPE in prod if you trip them:

- **Never write a user-scoped query without the owning user's UUID in the WHERE clause.** There is no row-level security at the DB; this is the only tenant boundary.
- **Never use single-arg `Comparator.comparing(extractor)` for entity fields.** Pair with `Comparator.nullsLast(Comparator.naturalOrder())` (or `thenComparingLong` for primitive `long`). JDK 25's `TreeMap.addEntryToEmptyMap` calls `compare(key, key)` on first insertion; pre-persist nulls (UUID, audit fields, JWT-derived fields) NPE inside the unwrapped lambda.
- **Never ignore the return value of `attachWithSession`.** Orphan-removal and dirty-checking only work on the managed copy returned by `merge()` — chain via `.map(fooRepository::attachWithSession)`.
- **Never add `@RunOnVirtualThread`.** Undertow's worker pool is already a virtual-thread factory; the annotation is redundant and misleading.
- **Never forget `@RegisterForReflection` on reflection-only DTOs.** It passes every JVM-mode test and fails only in the native Lambda build.
