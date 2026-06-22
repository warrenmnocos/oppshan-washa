---
name: feedback_code_style_and_testing
description: Code-style and testing preferences — copy oppshan-files style exactly, final var, verbose Jakarta + schema, shouldXxx test names, prefer integration tests
type: feedback
---

Warren wants washa to mirror the sibling `oppshan-files` project's style and conventions exactly,
and corrected several specifics across the session.

**Rule:**
- **Copy `oppshan-files` style element-for-element.** Entity template: `Serializable` + `@Serial`,
  `@Basic(optional=false)` on non-null columns, fully-specified `@Column`, `@Table` indexes +
  `@UniqueConstraint`, fluent setters, field-based `equals`/`hashCode`, nested `Comparator` enum for
  ordered entities. Canonical templates: `UserAccount.java`, `IdpAccount.java`.
- **`final var` for all local variables.** No bare `var`, no explicit type unless inference fails.
- **Use `List.getFirst()`, not `.get(0)`** (Java 21+ SequencedCollection). Prefer the modern
  sequenced-collection accessors (`getFirst`/`getLast`) over index access.
- **Verbose Jakarta annotations, and every `@Table` includes `schema = "oppshan"`** (washa app
  objects live in the `oppshan` schema of the `washa` database, not `public`; Flyway is configured
  with `schemas=oppshan` + `create-schemas=true` and Hibernate `default-schema=oppshan`). Don't
  name FKs in `@JoinColumn` — FK names live in Flyway.
- **Test method names ALWAYS start with `should`** (`shouldXxx`/`shouldXxxWhenYyy`), Java `@Test`
  and TS `it('should …')` alike. Never `test_…`, never a noun phrase. Spell out abbreviations.
- **Assertions: Hamcrest only — no AssertJ.** `oppshan-files` uses Hamcrest exclusively
  (`assertThat(x, is(...))` / `equalTo` / `comparesEqualTo` / `hasSize` / `closeTo`), which is also
  the Quarkus + REST Assured idiom (one matcher library across HTTP and non-HTTP tests). Use JUnit 5
  `assertThrows` for exceptions. Do NOT add AssertJ. (Lesson: check the reference before picking a
  library — an AssertJ detour here had to be reverted.)
- **Prefer integration tests (`@QuarkusTest`) over mock-heavy unit tests.** Exercise the real stack
  (real repos on Dev Services, OIDC test server / `@TestSecurity`, REST Assured). Plain JUnit only
  for pure infra-free logic (formula evaluator, parsers, pipes). Aim for ~100% line coverage via IT.
- **Member ordering by visibility: public → protected → package-private → private.** Within a
  class, declare/define members in descending visibility; private helpers go at the bottom.
  (Fields/constructor stay at the top per the entity template.)
- **Fluent/builder chains: ONE method call per line (always).** `new X()` on the first line, then
  every `.setFoo(...)` on its own indented continuation line — never multiple `.setX().setY()` on a
  line. Example:
  ```java
  final var debt = new Debt()
          .setBudgetMonth(month)
          .setOrdinal(0)
          .setName("Home mortgage")
          .setPrincipal(new BigDecimal("5000000"));
  ```
  This is a strict, repeatedly-stated preference and applies to production and test code alike.
- **Newline/spacing — favor newlines generously for readability (always).** One statement per
  line; never cram statements onto one dense line. Imports grouped (non-`java.*`, blank line, then
  `java.*`); blank line after the class brace; blank line between members; blank line before a
  terminal `return` after a block. Applies to docs/markdown too — break dense lines.

**Why:** consistency with the reference project Warren maintains; he reviews against it closely.

**How to apply:** when writing/editing any Java or test code, open the matching `oppshan-files`
file and follow its structure; expand simplified snippets to the full template before committing.
