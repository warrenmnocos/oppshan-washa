-- Unify the deduction/variable discriminant on `type` (was `kind`), backed by the DeductionType and
-- VariableType enums (and `base` by DeductionBase). The relational columns store the enum name
-- (UPPER_CASE) via @Enumerated(STRING); JSON exchange uses the lowercase value via @JsonValue. The
-- CHECK constraints reference the column, so they are dropped before the rename and recreated against
-- the renamed column with the UPPER_CASE set (db conventions A.13). Existing lowercase values are
-- upper-cased in place, and the vestigial 'computed' value is dropped from the set.

ALTER TABLE income_deduction DROP CONSTRAINT ck_income_deduction_kind;
ALTER TABLE income_deduction RENAME COLUMN kind TO type;
UPDATE income_deduction SET type = upper(type);
UPDATE income_deduction SET base = upper(base) WHERE base IS NOT NULL;
ALTER TABLE income_deduction ALTER COLUMN type SET DEFAULT 'FIXED';
ALTER TABLE income_deduction ADD CONSTRAINT ck_income_deduction_type
    CHECK (type IN ('PCT', 'FIXED', 'FORMULA', 'BRACKETS'));

ALTER TABLE income_variable DROP CONSTRAINT ck_income_variable_kind;
ALTER TABLE income_variable RENAME COLUMN kind TO type;
UPDATE income_variable SET type = upper(type);
UPDATE income_variable SET base = upper(base) WHERE base IS NOT NULL;
ALTER TABLE income_variable ALTER COLUMN type SET DEFAULT 'FORMULA';
ALTER TABLE income_variable ADD CONSTRAINT ck_income_variable_type
    CHECK (type IN ('PCT', 'FIXED', 'FORMULA', 'BRACKETS'));
