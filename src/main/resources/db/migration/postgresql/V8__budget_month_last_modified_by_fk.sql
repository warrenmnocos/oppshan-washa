-- Make budget_month.last_modified_by a real foreign key to the user who last saved the month; it was
-- a bare, unconstrained UUID. Nullable: a month that has never been saved through the API has no
-- modifier. Existing rows hold no value (the endpoint previously passed null), so nothing violates it.

ALTER TABLE budget_month ADD CONSTRAINT fk_budget_month_last_modified_by
    FOREIGN KEY (last_modified_by) REFERENCES user_account (uuid);
