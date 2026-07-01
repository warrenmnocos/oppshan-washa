package com.oppshan.washa.budget;

import io.quarkus.runtime.annotations.RegisterForReflection;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * Live computed figures for a month, all in base currency. {@code moneyOut} sums every allocation
 * (expenses including the derived {@code tithe}, all goal contributions, and debt as amortization
 * plus prepayment), so {@code free} is the cash left once the month is fully planned.
 * The category totals ({@code tithe}, {@code otherExpenses}, {@code debt}, {@code savingsGoals},
 * {@code nonSavingsGoals}) break {@code moneyOut} down by category; {@code savingsRate} is the share
 * of net income saved or left free:
 * {@code (moneyIn − expenses − tithe − nonSavingsGoals − debtAmortization) / moneyIn}.
 *
 * <p>{@code goalProgress} carries one {@link GoalProgress} per goal, and {@code savingsBalance} is
 * the running total held across every non-closed savings-flagged goal. Both derive
 * from the cumulative contributions summed across month rows, never stored. {@code activity} lists
 * this month's goal withdrawals and the goals closed this month. {@code prepayYear} totals each
 * prepayment-flagged debt's principal prepayment across this year's saved months.
 *
 * <p>{@code salaryNet} is the flat name→net map; {@code salaryBreakdown} carries the full deduction
 * breakdown per salary, in income order: each pay component summed to a gross subtotal, then each
 * deduction as a negative line, then net. Each {@link SalaryBreakdown#net()} equals
 * the matching {@code salaryNet} value in the salary's own currency, before conversion to base.
 */
@RegisterForReflection
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
        List<SalaryBreakdown> salaryBreakdown,
        List<DebtProjection> debts,
        List<GoalProgress> goalProgress,
        BigDecimal savingsBalance,
        List<Activity> activity,
        List<PrepayYear> prepayYear) {

    /**
     * The full deduction breakdown of one salary, all in the salary's own currency. {@code gross}
     * is the sum of its pay components; {@code deductions} lists each computed deduction line in
     * evaluation order; {@code net} is {@code gross} less the deductions and equals this salary's
     * entry in {@code salaryNet} before conversion to base currency. Mirrors the prototype's income
     * block: a gross subtotal, each deduction as a negative line, then net.
     */
    @RegisterForReflection
    public record SalaryBreakdown(String name,
                                  String currency,
                                  BigDecimal gross,
                                  List<DeductionLineView> deductions,
                                  BigDecimal net) {
    }

    /** One deduction line of a {@link SalaryBreakdown}: its label and computed amount (positive). */
    @RegisterForReflection
    public record DeductionLineView(String label, BigDecimal amount) {
    }

    /**
     * Payoff projection for one debt. {@code months}/{@code totalInterest} are the baseline (no extra
     * prepayment); {@code prepayMonths}/{@code prepayInterest} re-run the simulation with the debt's
     * annual principal prepayment (equal to the baseline when prepayment is off). The gap between the
     * two pairs is the months and interest saved.
     */
    @RegisterForReflection
    public record DebtProjection(String name,
                                 int months,
                                 BigDecimal totalInterest,
                                 int prepayMonths,
                                 BigDecimal prepayInterest) {
    }

    /**
     * Accumulated standing of one goal (all amounts in base currency). {@code balance} is the
     * cumulative contributions before this month plus this month's net contribution
     * ({@code amount − withdrawal}), floored at zero. For an amount target {@code target} is the
     * fixed goal amount and for a relative target it is {@code mult × net}; both leave {@code pct}
     * as {@code balance / target} clamped to {@code [0, 1]}. For a TIME target {@code target} is
     * null and {@code pct} is the share of elapsed time toward the due date, clamped to
     * {@code [0, 1]}. Open goals carry a null {@code target} and {@code pct}. {@code complete} is
     * true once a targeted goal's balance reaches its amount/relative target, or once a TIME goal's
     * due date has passed. {@code closed} mirrors the goal's close state. A closed or complete goal
     * keeps its balance here but stops contributing to money-out.
     */
    @RegisterForReflection
    public record GoalProgress(String label,
                               String currency,
                               BigDecimal balance,
                               BigDecimal target,
                               BigDecimal pct,
                               boolean savings,
                               boolean complete,
                               boolean closed) {
    }

    /**
     * One row of this month's goal activity (amount in base currency): either a withdrawal taken
     * this month or a goal closed this month. {@code kind} discriminates the two
     * ({@code "withdrawal"} / {@code "closed"}); {@code amount} carries the withdrawn amount for a
     * withdrawal and the remaining balance for a closure.
     */
    @RegisterForReflection
    public record Activity(String label,
                           String currency,
                           BigDecimal amount,
                           String kind) {
    }

    /**
     * One prepayment-flagged debt's principal prepayment accumulated across this year's saved months
     * (plus the month being planned), matched across months by name. {@code amount} is in the debt's
     * own currency; {@code amountBase} is the same total reduced to base currency, so a mixed-currency
     * set can be summed. Derived by summing month rows at the current rates, never stored.
     */
    @RegisterForReflection
    public record PrepayYear(String name,
                             String currency,
                             BigDecimal amount,
                             BigDecimal amountBase) {
    }
}
