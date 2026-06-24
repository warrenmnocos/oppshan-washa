-- Model the tax-bracket comparison op and contribution type as enums (BracketOp, BracketType). The
-- columns store the UPPER_CASE enum name via @Enumerated(STRING); JSON exchange uses the lowercase
-- value via @JsonValue. Existing lowercase values are upper-cased in place, and CHECK constraints are
-- added to match the enums (these columns had none).

UPDATE salary_bracket SET op = upper(op) WHERE op IS NOT NULL;
UPDATE salary_bracket SET type = upper(type) WHERE type IS NOT NULL;
ALTER TABLE salary_bracket ADD CONSTRAINT ck_salary_bracket_op
    CHECK (op IN ('GT', 'GTE', 'LT', 'LTE', 'EQ'));
ALTER TABLE salary_bracket ADD CONSTRAINT ck_salary_bracket_type
    CHECK (type IN ('FIXED', 'FORMULA', 'PCTGROSS', 'PCTBASIC'));
