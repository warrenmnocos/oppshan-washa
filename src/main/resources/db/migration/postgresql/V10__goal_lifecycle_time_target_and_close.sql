-- Goal lifecycle: a TIME target (a due date OR a period from the goal's start) plus the close state.
-- TIME goals measure progress against elapsed time and stop contributing once the due date passes;
-- a closed goal stops contributing but keeps its balance and shows in this month's activity. The new
-- columns are additive and nullable (closed defaults FALSE), and the target_type CHECK is dropped and
-- recreated against the UPPER_CASE set extended with 'TIME' (it references the column).

ALTER TABLE goal ADD COLUMN target_due_date     DATE;
ALTER TABLE goal ADD COLUMN target_period_count INTEGER;
ALTER TABLE goal ADD COLUMN target_period_unit  VARCHAR(16);
ALTER TABLE goal ADD COLUMN closed              BOOLEAN     NOT NULL DEFAULT FALSE;
ALTER TABLE goal ADD COLUMN closed_key          VARCHAR(7);

ALTER TABLE goal DROP CONSTRAINT ck_goal_target_type;
ALTER TABLE goal ADD CONSTRAINT ck_goal_target_type
    CHECK (target_type IN ('OPEN', 'AMOUNT', 'RELATIVE', 'TIME'));
