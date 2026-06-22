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
- **Verbose Jakarta annotations, and every `@Table` includes `schema = "public"`** (the reference
  omits schema; washa adds it). Don't name FKs in `@JoinColumn` — FK names live in Flyway.
- **Test method names ALWAYS start with `should`** (`shouldXxx`/`shouldXxxWhenYyy`), Java `@Test`
  and TS `it('should …')` alike. Never `test_…`, never a noun phrase. Spell out abbreviations.
- **Prefer integration tests (`@QuarkusTest`) over mock-heavy unit tests.** Exercise the real stack
  (real repos on Dev Services, OIDC test server / `@TestSecurity`, REST Assured). Plain JUnit only
  for pure infra-free logic (formula evaluator, parsers, pipes). Aim for ~100% line coverage via IT.
- **Newline/spacing:** imports grouped (non-`java.*`, blank line, then `java.*`); blank line after
  the class brace; blank line between members; blank line before a terminal `return` after a block.

**Why:** consistency with the reference project Warren maintains; he reviews against it closely.

**How to apply:** when writing/editing any Java or test code, open the matching `oppshan-files`
file and follow its structure; expand simplified snippets to the full template before committing.
