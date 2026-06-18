Audit the backend Java code at `src/main/java/com/oppshan/washa/` and report problems grouped by severity.

This audit is heavy on file reads; **dispatch a `general-purpose` agent** to do it rather than running it in the main session.

## Required reading first (the agent does this)

1. `CLAUDE.md` (root) — § C.1 (MessageCode contract), § C.2 (commit conventions), § C.3 (scope), § C.4 (deployment).
2. `src/main/java/CLAUDE.md` — backend-specific conventions (entity Comparators with `nullsLast`, Jakarta Data patterns, named-query convention, native-image reflection rules, test seeding rules, `final var` / constructor-injection / method-ordering style).
3. The project memory index at `.claude/memory/MEMORY.md` (relative to the repo root) and any `feedback_*.md` files it references. These document established conventions; flag deviations as findings.

## Scope

Sweep every package under `com.oppshan.washa`. Also look at `src/main/resources/application.properties` and `src/main/resources/db/migration/postgresql/`.

## Look for, in priority order

**Correctness & security**
- Authorization gaps: any endpoint or service operating on user-scoped data without verifying ownership (the per-user filter present in every user-scoped query)
- SQL injection (raw SQL, string concatenation in JPQL, unparameterized native queries)
- Race conditions / TOCTOU: uniqueness checks, anything that reads-then-writes without a DB-level constraint
- Transaction boundaries: missing `@Transactional`, lost updates, dirty-checking gotchas
- Exception handling: swallowed exceptions, broad catches, `BusinessException` masked by generic catch-and-rewrap, stack-trace leakage to clients

**Performance**
- N+1 queries (loops touching lazy collections/proxies)
- Inline JPQL where `@NamedQuery` on entity is the convention
- Multiple round-trips for things expressible as one query
- Missing index implied by query shape

**Convention adherence**
- Inline JPQL instead of `@NamedQuery`
- `@RunOnVirtualThread` annotation present (redundant on Undertow)
- `@RegisterForReflection` missing on `*View` / `*Request` / `*Response` records (or sealed interfaces returned via JAX-RS) — native-image trap
- Test method names not following `shouldXxxYyy`
- Java locals not using `final var`
- Field-level injection instead of constructor injection
- Public methods mixed with private (private should be grouped at bottom)
- Single-arg `Comparator.comparing(extractor)` without `nullsLast` on entity Comparators

**Code quality**
- Dead code, unused imports
- Half-finished implementations / TODOs / FIXMEs
- Comments that describe WHAT (root guide says only WHY)
- Validation missing at boundaries
- Defensive checks for impossible scenarios

## Output format

Group findings by severity (**Critical / High / Medium / Low**). For each:
- One-line summary
- File path and line range (`path:line` format)
- Why it's a problem
- Suggested fix in one sentence

Cap at ~2000 words. Skip stylistic-only or pedantic findings — focus on things that actually matter. If a category turns up nothing, say so in one line. End with a "Categories with no findings" list to make the absence-of-evidence explicit.

**Read-only audit. Do not modify any files.**
