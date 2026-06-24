package com.oppshan.washa.budget;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * Live computed figures for a month (all in base currency), returned by {@code /api/budget/compute}.
 * {@code moneyOut} sums every allocation — expenses (including the derived {@code tithe}), all goal
 * contributions, and debt (amortization + prepayment) — so {@code free} is the cash left after the
 * month is fully planned (HANDOVER §4). The category totals back the allocation chart and the
 * summary metrics; {@code savingsRate} is the share of net income that is saved or left free:
 * {@code (moneyIn − expenses − nonSavingsGoals − debtAmortization) / moneyIn}.
 */
public record ComputedView(
        BigDecimal moneyIn,
        BigDecimal moneyOut,
        BigDecimal free,
        BigDecimal tithe,
        BigDecimal otherExpenses,
        BigDecimal debt,
        BigDecimal savingsGoals,
        BigDecimal nonSavingsGoals,
        BigDecimal savingsRate,
        Map<String, BigDecimal> salaryNet,
        List<DebtProjection> debts) {

    /**
     * Payoff projection for one debt. {@code months}/{@code totalInterest} are the baseline (no extra
     * prepayment); {@code prepayMonths}/{@code prepayInterest} re-run the simulation with the debt's
     * annual principal prepayment (equal to the baseline when prepayment is off), so the UI can show
     * the months and interest saved.
     */
    public record DebtProjection(String name,
                                 int months,
                                 BigDecimal totalInterest,
                                 int prepayMonths,
                                 BigDecimal prepayInterest) {
    }
}
