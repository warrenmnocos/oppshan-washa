package com.oppshan.washa.budget;

import java.math.BigDecimal;
import java.math.MathContext;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * An independent re-derivation of the figures {@link BudgetService#compute} returns, written from the rules rather than
 * from the service's code, for the scenario tests to check against. It reads only the raw month view and works at 120
 * significant digits, so its figures are effectively exact; the service works at 34, so the tests compare within a
 * tolerance far below a thousandth of a yen.
 *
 * <p>It assumes what {@link ComputeScenario} guarantees: no salary deductions (net is the summed components), no TIME
 * goals, and fresh goal and debt names, so nothing was contributed, repaid, or prepaid in earlier months. Under that
 * assumption no goal is complete (it would need an earlier balance to reach its target), every open goal takes its
 * contribution, and an interest-free repayment counts up to the amount borrowed.
 *
 * <p>Goal and debt percentages are left unrounded here ({@code pct}) and the savings rate too; the tests check the
 * service rounded them correctly.
 */
final class ComputeOracle {

    private static final MathContext EXACT = new MathContext(120);

    private static final BigDecimal TITHE_RATE = new BigDecimal("0.1");

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    /**
     * The months reported for a loan that never pays off (the page shows it as never amortizing).
     */
    static final int NEVER = Integer.MAX_VALUE;

    private ComputeOracle() {
    }

    /**
     * Every figure the month should produce, in the shapes {@link ComputedView} uses.
     */
    record Expected(BigDecimal moneyIn,
                    BigDecimal moneyOut,
                    BigDecimal free,
                    BigDecimal overBudgetBy,
                    BigDecimal tithe,
                    BigDecimal titheAllocated,
                    BigDecimal otherExpenses,
                    BigDecimal debt,
                    BigDecimal debtAmortization,
                    BigDecimal savingsGoals,
                    BigDecimal nonSavingsGoals,
                    BigDecimal savingsRateUnrounded,
                    Map<String, BigDecimal> salaryNet,
                    List<BigDecimal> salaryGross,
                    List<ComputedView.GoalProgress> goalProgress,
                    BigDecimal savingsBalance,
                    List<ComputedView.Activity> activity,
                    List<ComputedView.PrepayYear> prepayYear,
                    BigDecimal prepayYearTotal,
                    List<ComputedView.DebtProgress> debtProgress,
                    List<ComputedView.DebtProjection> debtProjections,
                    BigDecimal debtBalance,
                    List<ComputedView.CurrencyTotal> expensesByCurrency,
                    List<ComputedView.CurrencyTotal> goalsByCurrency,
                    List<ComputedView.CurrencyTotal> debtsByCurrency,
                    List<ComputedView.CurrencyTotal> moneyOutByCurrency) {
    }

    /**
     * Works out every figure for {@code view} computed as of {@code asOf}.
     */
    static Expected expect(BudgetMonthView view,
                           YearMonth asOf) {
        final var household = Objects.requireNonNullElse(view.cur(), List.<BudgetMonthView.CurrencyView>of());
        final var base = household.isEmpty() ? "JPY" : household.getFirst().code();
        final var rates = Objects.requireNonNullElse(view.fxRates(), Map.<String, BigDecimal>of());
        final var money = new Money(base, rates);

        final var salaryNet = new LinkedHashMap<String, BigDecimal>();
        final var salaryGross = new ArrayList<BigDecimal>();
        var moneyIn = BigDecimal.ZERO;
        for (final var salary : view.salaries()) {
            var gross = BigDecimal.ZERO;
            for (final var component : salary.components()) {
                gross = gross.add(orZero(component.amount()));
            }

            salaryGross.add(gross);
            final var net = money.toBase(gross, salary.currency());
            salaryNet.put(salary.name(), net);
            moneyIn = moneyIn.add(net);
        }

        final var tithe = moneyIn.multiply(TITHE_RATE);
        var titheAllocated = BigDecimal.ZERO;
        var otherExpenses = BigDecimal.ZERO;
        final var expensesByCurrency = new LinkedHashMap<String, BigDecimal>();
        for (final var expense : view.expenses()) {
            if ("tithe".equals(expense.auto())) {
                titheAllocated = tithe;
                expensesByCurrency.merge(base, tithe, BigDecimal::add);
            } else {
                final var amount = orZero(expense.amount());
                otherExpenses = otherExpenses.add(money.toBase(amount, expense.currency()));
                expensesByCurrency.merge(money.bucket(expense.currency()), amount, BigDecimal::add);
            }
        }

        var savingsGoals = BigDecimal.ZERO;
        var nonSavingsGoals = BigDecimal.ZERO;
        var savingsBalance = BigDecimal.ZERO;
        final var goalProgress = new ArrayList<ComputedView.GoalProgress>();
        final var activity = new ArrayList<ComputedView.Activity>();
        final var goalsByCurrency = new LinkedHashMap<String, BigDecimal>();
        for (final var goal : view.goals()) {
            final var contribution = money.toBase(orZero(goal.amount()), goal.currency());
            final var withdrawal = money.toBase(orZero(goal.withdrawal()), goal.currency());
            final var target = switch (goal.target().type()) {
                case AMOUNT -> goal.target().amount() == null
                        ? null
                        : money.toBase(goal.target().amount(), goal.currency());
                case RELATIVE -> goal.target().mult() == null ? null : goal.target().mult().multiply(moneyIn);
                case OPEN, TIME -> null;
            };

            final var active = !goal.closed();
            if (active && goal.savings()) {
                savingsGoals = savingsGoals.add(contribution);
            } else if (active) {
                nonSavingsGoals = nonSavingsGoals.add(contribution);
            }

            goalsByCurrency.merge(money.bucket(goal.currency()), active ? orZero(goal.amount()) : BigDecimal.ZERO,
                    BigDecimal::add);

            final var balance = (active ? contribution : BigDecimal.ZERO).subtract(withdrawal).max(BigDecimal.ZERO);
            final var pct = target == null || target.signum() <= 0
                    ? null
                    : balance.divide(target, EXACT).min(BigDecimal.ONE);
            goalProgress.add(new ComputedView.GoalProgress(goal.label(), goal.currency(), balance, target, pct,
                    goal.savings(), false, goal.closed()));

            if (goal.savings() && !goal.closed()) {
                savingsBalance = savingsBalance.add(balance);
            }

            if (withdrawal.signum() > 0) {
                activity.add(new ComputedView.Activity(goal.label(), goal.currency(), withdrawal, "withdrawal"));
            }

            if (goal.closed() && asOf.toString().equals(goal.closedKey())) {
                activity.add(new ComputedView.Activity(goal.label(), goal.currency(), balance, "closed"));
            }
        }

        var debtAmortization = BigDecimal.ZERO;
        var debtPrepayment = BigDecimal.ZERO;
        var debtBalance = BigDecimal.ZERO;
        final var prepayYear = new ArrayList<ComputedView.PrepayYear>();
        final var debtProgress = new ArrayList<ComputedView.DebtProgress>();
        final var debtProjections = new ArrayList<ComputedView.DebtProjection>();
        final var debtsByCurrency = new LinkedHashMap<String, BigDecimal>();
        for (final var debt : view.debts()) {
            if (debt.interestFree()) {
                final var borrowed = orZero(debt.principal()).max(BigDecimal.ZERO);
                final var repayment = orZero(debt.monthly()).max(BigDecimal.ZERO).min(borrowed);
                final var balance = borrowed.subtract(repayment);
                debtAmortization = debtAmortization.add(money.toBase(repayment, debt.currency()));
                debtsByCurrency.merge(money.bucket(debt.currency()), repayment, BigDecimal::add);
                final var balanceBase = money.toBase(balance, debt.currency());
                debtBalance = debtBalance.add(balanceBase);
                debtProgress.add(new ComputedView.DebtProgress(debt.name(), debt.currency(), true, balance, balanceBase,
                        borrowed, repayment, repayment,
                        borrowed.signum() > 0 ? repayment.divide(borrowed, EXACT) : null,
                        balance.signum() <= 0));
                continue;
            }

            final var monthly = orZero(debt.monthly());
            debtAmortization = debtAmortization.add(money.toBase(monthly, debt.currency()));
            debtsByCurrency.merge(money.bucket(debt.currency()), monthly, BigDecimal::add);

            var annualPrepayment = BigDecimal.ZERO;
            if (debt.prepay()) {
                final var prepayCurrency = debt.prepayCurrency() == null ? debt.currency() : debt.prepayCurrency();
                final var prepayAmount = orZero(debt.prepayAmount());
                final var prepayBase = money.toBase(prepayAmount, prepayCurrency);
                debtPrepayment = debtPrepayment.add(prepayBase);
                debtsByCurrency.merge(money.bucket(prepayCurrency), prepayAmount, BigDecimal::add);
                annualPrepayment = prepayBase.multiply(money.rate(debt.currency()));
                prepayYear.add(
                        new ComputedView.PrepayYear(debt.name(), debt.currency(), annualPrepayment, prepayBase)
                );
            }

            final var baseline = amortize(debt, BigDecimal.ZERO);
            final var withPrepayment = annualPrepayment.signum() > 0 ? amortize(debt, annualPrepayment) : baseline;
            debtProjections.add(new ComputedView.DebtProjection(debt.name(), baseline.months(), baseline.interest(),
                    withPrepayment.months(), withPrepayment.interest()));

            final var principal = orZero(debt.principal());
            final var principalBase = money.toBase(principal, debt.currency());
            debtBalance = debtBalance.add(principalBase);
            debtProgress.add(new ComputedView.DebtProgress(debt.name(), debt.currency(), false, principal,
                    principalBase, null, null, null, null, false));
        }

        final var debt = debtAmortization.add(debtPrepayment);
        final var moneyOut = otherExpenses.add(titheAllocated).add(savingsGoals).add(nonSavingsGoals).add(debt);
        final var free = moneyIn.subtract(moneyOut);
        final var savingsRate = moneyIn.signum() <= 0
                ? BigDecimal.ZERO
                : moneyIn.subtract(otherExpenses).subtract(titheAllocated).subtract(nonSavingsGoals)
                        .subtract(debtAmortization).multiply(HUNDRED).divide(moneyIn, EXACT);

        final var moneyOutByCurrency = new LinkedHashMap<String, BigDecimal>();
        for (final var section : List.of(expensesByCurrency, goalsByCurrency, debtsByCurrency)) {
            section.forEach((currency, amount) -> moneyOutByCurrency.merge(currency, amount, BigDecimal::add));
        }

        return new Expected(
                moneyIn,
                moneyOut,
                free,
                moneyOut.subtract(moneyIn).max(BigDecimal.ZERO),
                tithe,
                titheAllocated,
                otherExpenses,
                debt,
                debtAmortization,
                savingsGoals,
                nonSavingsGoals,
                savingsRate,
                salaryNet,
                salaryGross,
                goalProgress,
                savingsBalance,
                activity,
                prepayYear,
                prepayYear.stream().map(ComputedView.PrepayYear::amountBase).reduce(BigDecimal.ZERO, BigDecimal::add),
                debtProgress,
                debtProjections,
                debtBalance,
                inHouseholdOrder(expensesByCurrency, household),
                inHouseholdOrder(goalsByCurrency, household),
                inHouseholdOrder(debtsByCurrency, household),
                inHouseholdOrder(moneyOutByCurrency, household)
        );
    }

    /**
     * Converts {@code amount} in {@code currency} to the base currency at 120 digits. Exposed so the tests can check a
     * section's per-currency figures add back up to its base total.
     */
    static BigDecimal toBase(BudgetMonthView view,
                             BigDecimal amount,
                             String currency) {
        final var household = Objects.requireNonNullElse(view.cur(), List.<BudgetMonthView.CurrencyView>of());
        final var base = household.isEmpty() ? "JPY" : household.getFirst().code();
        return new Money(base, Objects.requireNonNullElse(view.fxRates(), Map.of())).toBase(amount, currency);
    }

    /**
     * The household currency-list order, then any currency the list doesn't carry in the order its lines appear.
     */
    private static List<ComputedView.CurrencyTotal> inHouseholdOrder(Map<String, BigDecimal> totals,
                                                                     List<BudgetMonthView.CurrencyView> household) {
        final var remaining = new LinkedHashMap<>(totals);
        final var ordered = new ArrayList<ComputedView.CurrencyTotal>();
        for (final var currency : household) {
            if (remaining.containsKey(currency.code())) {
                ordered.add(new ComputedView.CurrencyTotal(currency.code(), remaining.remove(currency.code())));
            }
        }

        remaining.forEach((currency, amount) -> ordered.add(new ComputedView.CurrencyTotal(currency, amount)));
        return ordered;
    }

    /**
     * Months to pay off an interest-bearing debt and the interest paid on the way, the way a lender's schedule runs:
     * each month the balance accrues a twelfth of the annual rate, the payment covers that interest and pays down the
     * rest, and every twelfth month {@code annualPrepayment} (in the debt's currency) comes off the principal too.
     * The payoff month is the one the balance reaches zero in. A loan whose payment can't cover a month's interest
     * never pays off unless a prepayment is helping, and nothing runs past 1,200 months (one hundred years); both
     * report {@link ComputeOracle#NEVER} months. The scenarios have no rate steps, so the payment never changes.
     */
    private static Amortization amortize(BudgetMonthView.DebtView debt,
                                         BigDecimal annualPrepayment) {
        final var monthlyRate = orZero(debt.annualRate()).divide(HUNDRED, EXACT).divide(BigDecimal.valueOf(12), EXACT);
        final var payment = orZero(debt.monthly());
        var balance = orZero(debt.principal());
        var interestPaid = BigDecimal.ZERO;
        for (var month = 1; month <= 1200; month++) {
            final var interest = balance.multiply(monthlyRate, EXACT);
            if (payment.compareTo(interest) <= 0 && annualPrepayment.signum() == 0) {
                return new Amortization(NEVER, interestPaid);
            }

            interestPaid = interestPaid.add(interest);
            balance = balance.subtract(payment.subtract(interest));
            if (month % 12 == 0) {
                balance = balance.subtract(annualPrepayment);
            }

            if (balance.signum() <= 0) {
                return new Amortization(month, interestPaid);
            }
        }

        return new Amortization(NEVER, interestPaid);
    }

    /**
     * Months to payoff ({@link #NEVER} when it never pays off) and the interest paid along the way.
     */
    private record Amortization(int months,
                                BigDecimal interest) {
    }

    private static BigDecimal orZero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    /**
     * The month's exchange rates, read the way the household sets them: units of a currency per one base unit, the
     * base itself (and a missing or unset code) at one, and any currency without a positive rate at one too.
     */
    private record Money(String base,
                         Map<String, BigDecimal> rates) {

        private BigDecimal rate(String currency) {
            if (currency == null || currency.equalsIgnoreCase(base)) {
                return BigDecimal.ONE;
            }

            final var rate = rates.get(currency);
            return rate == null || rate.signum() == 0 ? BigDecimal.ONE : rate;
        }

        private BigDecimal toBase(BigDecimal amount,
                                  String currency) {
            return amount.divide(rate(currency), EXACT);
        }

        private String bucket(String currency) {
            return currency == null ? base : currency;
        }
    }
}
