---
name: feedback_persistence_patterns
description: Persistence preferences — always relational (never JSONB), repository pattern (no EntityManager in services), BigDecimal money, YearMonth via converter, temporal @Version audit
type: feedback
---

Warren corrected several persistence choices during the build; these are firm preferences.

**Rule:**
- **Always relational, never JSONB.** Normalize even nested/polymorphic structures (e.g. the
  salary payroll engine: components, deductions, variables, additive brackets) into typed tables
  with real FKs. He explicitly overrode a handover doc that suggested JSONB: "always use relational
  style; if a doc doesn't recommend relational, override it." For a child that can belong to one of
  several parents, use multiple nullable FK columns + a CHECK that exactly one is set.
- **No `EntityManager` in the service layer.** Services call repositories only. Stateful-session
  writes (the `@UuidGenerator` and cascades that Jakarta Data's stateless `save` won't run) live in
  a `StatefulWriteRepository<T>` mixin (`insertWithSession`/`updateWithSession`/`attachWithSession`/
  `deleteWithSession`/`flushWithSession`). Endpoints call services; services call repositories.
- **Money is `BigDecimal` + a 3-letter currency code + `currency_setting`** — NOT JavaMoney/JSR-354
  (its SPI/reflection model fights GraalVM native, and its FX-provider model conflicts with washa's
  conservative per-month snapshot rates).
- **`java.time.YearMonth` for month keys**, persisted to `CHAR(7)` via a global
  `@Converter(autoApply=true) AttributeConverter<YearMonth,String>` (Hibernate has no native type;
  the "YYYY-MM" form is lexically sortable so range queries stay correct).
- **Audit on every entity via a `@MappedSuperclass`:** `created_at` (`@CreationTimestamp`) and
  `last_modified_at` which IS the temporal optimistic-lock `@Version` (Hibernate stamps it on
  flush). Never set these manually; never add a separate `@UpdateTimestamp`. A `UuidEntity` subclass
  adds the VERSION_7 UUID id; natural-key tables extend `AuditableEntity` directly.

**Why:** queryable typed schemas, clean layering, native-image friendliness, lock-token reuse.

**How to apply:** new entities extend the audit base; new repos extend the mixin; new queries are
index-backed (see backend `CLAUDE.md` A.4 for the mixin processor gotcha and A.9/V-migrations).
