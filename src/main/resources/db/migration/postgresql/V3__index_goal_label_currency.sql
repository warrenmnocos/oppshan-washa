-- The cumulative goal-progress query filters goals by (label, currency) across months
-- (BudgetService.cumulativeGoalProgressBefore). Without this index it scans the goal table;
-- with it the filter is index-backed. fx_rate base-currency lookups use the composite PK's
-- leading column (base_currency), so they need no separate index.
CREATE INDEX idx_goal_label_currency ON goal (label, currency);
