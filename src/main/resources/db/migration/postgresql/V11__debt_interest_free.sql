-- Interest-free debts: money owed with no interest (a loan from a friend or colleague). Such a debt skips the
-- amortization simulation and the prepayment figures; its monthly amount is that month's repayment, and its progress
-- is the repayments summed across months against the principal borrowed. The column is additive and defaults FALSE,
-- so every existing debt stays interest-bearing.

ALTER TABLE debt
    ADD COLUMN interest_free BOOLEAN NOT NULL DEFAULT FALSE;
