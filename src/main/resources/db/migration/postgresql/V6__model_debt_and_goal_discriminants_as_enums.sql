-- Model the debt reprice mode and the goal target type as enums (DebtRepriceMode, GoalTargetType),
-- joining the deduction/variable type enums from V5. The relational columns store the UPPER_CASE enum
-- name via @Enumerated(STRING); JSON exchange uses the lowercase value via @JsonValue. Existing
-- lowercase values are upper-cased in place. debt.reprice_mode gains a CHECK (it had none); the goal
-- target_type CHECK is dropped first and recreated against the UPPER_CASE set (it references the column).

UPDATE debt SET reprice_mode = upper(reprice_mode) WHERE reprice_mode IS NOT NULL;
ALTER TABLE debt ADD CONSTRAINT ck_debt_reprice_mode
    CHECK (reprice_mode IN ('PAYMENT', 'TERM'));

ALTER TABLE goal DROP CONSTRAINT ck_goal_target_type;
UPDATE goal SET target_type = upper(target_type);
ALTER TABLE goal ALTER COLUMN target_type SET DEFAULT 'OPEN';
ALTER TABLE goal ADD CONSTRAINT ck_goal_target_type
    CHECK (target_type IN ('OPEN', 'AMOUNT', 'RELATIVE'));
