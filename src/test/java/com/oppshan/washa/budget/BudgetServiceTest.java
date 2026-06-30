package com.oppshan.washa.budget;

import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

@QuarkusTest
class BudgetServiceTest {

    @Inject
    BudgetService budgetService;

    @Inject
    FxRateRepository fxRateRepository;

    @Inject
    BudgetMonthRepository budgetMonthRepository;

    // A well-separated base year-month per test so seeded months never collide on the year_month
    // unique constraint. The base is random per JVM run — within four digits, since the column is
    // varchar(7) "YYYY-MM" — so a reused test DB can't clash run-to-run, then steps sequentially by
    // 100 years so two tests within a run can't roll the same value (which the old per-test random
    // year occasionally did and flaked CI). Ten call-sites × 100, plus a ±1-year margin, stays < 9999.
    private static final AtomicInteger BASE_YEAR =
            new AtomicInteger(1000 + ThreadLocalRandom.current().nextInt(6000));

    private static YearMonth nextBaseMonth() {
        return YearMonth.of(BASE_YEAR.getAndAdd(100), 1);
    }

    private Income simpleSalary(BudgetMonth month, String name, String currency, String basicAmount) {
        final var income = new Income().setBudgetMonth(month).setOrdinal(0)
                .setName(name).setCurrency(currency).setEngine("generic");
        income.getComponents().add(new IncomeComponent().setIncome(income).setOrdinal(0)
                .setLabel("Basic salary").setAmount(new BigDecimal(basicAmount)).setTaxable(true).setBasic(true));
        return income;
    }

    @Test
    void shouldCombineNetAcrossCurrenciesAndComputeTithe() {
        QuarkusTransaction.requiringNew().run(() -> {
            // Force JPY→PHP to 0.36. saveMonth now upserts a month's fx rates, so another test in the
            // run may have persisted a different JPY→PHP rate, and combinedNet reads the stored rate;
            // setting it (rather than skipping when one exists) keeps this test in control (A.10).
            final var id = new FxRateId("JPY", "PHP");
            fxRateRepository.findById(id).ifPresentOrElse(
                    existing -> fxRateRepository.updateWithSession(
                            existing.setRate(new BigDecimal("0.36")).setCapturedAt(Instant.now())),
                    () -> fxRateRepository.insertWithSession(new FxRate().setId(id)
                            .setRate(new BigDecimal("0.36")).setCapturedAt(Instant.now())));
        });

        // In-memory month (no deductions → net == gross): 100,000 JPY + 360 PHP (== 1,000 JPY).
        final var month = new BudgetMonth().setYearMonth(YearMonth.of(2026, 6)).setBaseCurrency("JPY");
        month.getIncomes().add(simpleSalary(month, "Alice", "JPY", "100000"));
        month.getIncomes().add(simpleSalary(month, "Bob", "PHP", "360"));

        final var combinedNet = QuarkusTransaction.requiringNew().call(() -> budgetService.combinedNet(month));
        final var tithe = QuarkusTransaction.requiringNew().call(() -> budgetService.tithe(month));

        assertThat(combinedNet, is(comparesEqualTo(new BigDecimal("101000"))));
        assertThat(tithe, is(comparesEqualTo(new BigDecimal("10100"))));
    }

    @Test
    void shouldSumGoalContributionsAcrossPriorMonths() {
        // Unique goal label + unique base year per run so the cumulative query sees only this run's
        // goals and the months never collide with other tests on the shared, reused test DB.
        final var label = "NISA-" + UUID.randomUUID();
        final var base = nextBaseMonth();
        seedNisaGoal(base, label, "100000");
        seedNisaGoal(base.plusMonths(1), label, "100000");
        seedNisaGoal(base.plusMonths(2), label, "100000");

        final var prior = QuarkusTransaction.requiringNew().call(() ->
                budgetService.cumulativeGoalProgressBefore(label, "JPY", base.plusMonths(2)));

        assertThat(prior, is(comparesEqualTo(new BigDecimal("200000")))); // base + base+1, not base+2
    }

    @Test
    void shouldComputeAllocationBreakdownAndSavingsRate() {
        final var salary = new BudgetMonthView.SalaryView("Alice", "JPY", "generic",
                List.of(new BudgetMonthView.ComponentView("Basic", new BigDecimal("500000"), true, true, null, false)),
                List.of(),
                List.of());
        final var view = new BudgetMonthView(
                List.of(salary),
                List.of(
                        new BudgetMonthView.ExpenseView("Tithe", null, "JPY", "tithe"),
                        new BudgetMonthView.ExpenseView("Rent", new BigDecimal("100000"), "JPY", null)),
                List.of(
                        new BudgetMonthView.GoalView("NISA", new BigDecimal("80000"), "JPY",
                                new BudgetMonthView.TargetView(GoalTargetType.OPEN, null, null, null, null, null, null), true, null, false, null),
                        new BudgetMonthView.GoalView("Trip", new BigDecimal("30000"), "JPY",
                                new BudgetMonthView.TargetView(GoalTargetType.OPEN, null, null, null, null, null, null), false, null, false, null)),
                List.of(new BudgetMonthView.DebtView("Loan", new BigDecimal("5000000"), new BigDecimal("5"),
                        new BigDecimal("40000"), 240, DebtRepriceMode.PAYMENT, "JPY", true, new BigDecimal("10000"), "JPY", List.of())),
                List.of(new BudgetMonthView.CurrencyView("JPY", "¥")));

        final var result = QuarkusTransaction.requiringNew().call(() -> budgetService.compute(view));

        assertThat(result.moneyIn(), is(comparesEqualTo(new BigDecimal("500000"))));     // generic net == gross
        assertThat(result.tithe(), is(comparesEqualTo(new BigDecimal("50000"))));        // 10% of net
        assertThat(result.otherExpenses(), is(comparesEqualTo(new BigDecimal("100000")))); // rent only, not tithe
        assertThat(result.savingsGoals(), is(comparesEqualTo(new BigDecimal("80000"))));
        assertThat(result.nonSavingsGoals(), is(comparesEqualTo(new BigDecimal("30000"))));
        assertThat(result.debt(), is(comparesEqualTo(new BigDecimal("50000"))));          // 40k amort + 10k prepay
        assertThat(result.moneyOut(), is(comparesEqualTo(new BigDecimal("310000"))));      // 100k+50k+80k+30k+50k
        assertThat(result.free(), is(comparesEqualTo(new BigDecimal("190000"))));
        // (500000 − 100000 expenses − 50000 tithe − 30000 non-savings goal − 40000 debt amort) / 500000
        assertThat(result.savingsRate(), is(comparesEqualTo(new BigDecimal("56.0"))));

        // The debt amortizes, and the annual prepayment pays it off sooner for less total interest.
        final var loan = result.debts().getFirst();
        assertThat(loan.name(), is("Loan"));
        assertThat(loan.months(), is(both(greaterThan(0)).and(not(2147483647))));
        assertThat(loan.prepayMonths(), is(lessThan(loan.months())));
        assertThat(loan.prepayInterest(), is(lessThan(loan.totalInterest())));
    }

    @Test
    void shouldExposeSalaryDeductionBreakdownInIncomeOrder() {
        // One salary, 500k gross (single basic component), with two deductions evaluated in order:
        // 10% of gross (50k) then a fixed 8k. Net = 500k − 58k = 442k. The currency is the base
        // currency, so the breakdown's net equals this salary's salaryNet entry (no conversion).
        final var salary = new BudgetMonthView.SalaryView("Alice", "JPY", "generic",
                List.of(new BudgetMonthView.ComponentView("Basic", new BigDecimal("500000"), true, true, null, false)),
                List.of(
                        new BudgetMonthView.DeductionView("Pension", DeductionType.PCT, DeductionBase.GROSS, null,
                                new BigDecimal("10"), null, null, null, null, null, false, null, false, List.of()),
                        new BudgetMonthView.DeductionView("Union dues", DeductionType.FIXED, null, null,
                                null, null, null, new BigDecimal("8000"), null, null, false, null, false, List.of())),
                List.of());
        final var view = new BudgetMonthView(List.of(salary), List.of(), List.of(), List.of(),
                List.of(new BudgetMonthView.CurrencyView("JPY", "¥")));

        final var result = QuarkusTransaction.requiringNew().call(() -> budgetService.compute(view));

        assertThat(result.salaryBreakdown(), hasSize(1));
        final var breakdown = result.salaryBreakdown().getFirst();
        assertThat(breakdown.name(), is("Alice"));
        assertThat(breakdown.currency(), is("JPY"));
        assertThat(breakdown.gross(), is(comparesEqualTo(new BigDecimal("500000"))));
        assertThat(breakdown.net(), is(comparesEqualTo(new BigDecimal("442000"))));

        // Each deduction line carries its label and computed (positive) amount, in evaluation order.
        final var lines = breakdown.deductions();
        assertThat(lines, hasSize(2));
        assertThat(lines.get(0).label(), is("Pension"));
        assertThat(lines.get(0).amount(), is(comparesEqualTo(new BigDecimal("50000"))));
        assertThat(lines.get(1).label(), is("Union dues"));
        assertThat(lines.get(1).amount(), is(comparesEqualTo(new BigDecimal("8000"))));

        // The breakdown's net is consistent with the flat salaryNet map (same currency → no conversion).
        assertThat(breakdown.net(), is(comparesEqualTo(result.salaryNet().get("Alice"))));
    }

    @Test
    void shouldComputeGoalProgressBalancesAndSavingsBalance() {
        // Unique labels + a far-future base year so the prior-month query and the per-month inserts
        // never collide with other tests on the shared, reused test DB.
        final var nisaLabel = "NISA-" + UUID.randomUUID();
        final var tripLabel = "Trip-" + UUID.randomUUID();
        final var base = nextBaseMonth();
        final var asOf = base.plusMonths(2);

        // Prior contributions: NISA (savings) gets 100k in each of the two months before `asOf`;
        // Trip (non-savings) gets 50k in the second of those months. Both goals share that month, so
        // they go in one BudgetMonth insert (one row per year_month).
        seedMonth(base, new SeedGoal(nisaLabel, "100000", true));
        seedMonth(base.plusMonths(1), new SeedGoal(nisaLabel, "100000", true), new SeedGoal(tripLabel, "50000", false));

        // The month being planned (`asOf`): NISA adds 80k toward a 300k amount target; Trip adds 30k.
        final var view = new BudgetMonthView(
                List.of(),
                List.of(),
                List.of(
                        new BudgetMonthView.GoalView(nisaLabel, new BigDecimal("80000"), "JPY",
                                new BudgetMonthView.TargetView(GoalTargetType.AMOUNT, new BigDecimal("300000"), null, null, null, null, null), true, null, false, null),
                        new BudgetMonthView.GoalView(tripLabel, new BigDecimal("30000"), "JPY",
                                new BudgetMonthView.TargetView(GoalTargetType.OPEN, null, null, null, null, null, null), false, null, false, null)),
                List.of(),
                List.of(new BudgetMonthView.CurrencyView("JPY", "¥")));

        final var result = QuarkusTransaction.requiringNew().call(() -> budgetService.compute(view, asOf));

        final var nisa = result.goalProgress().stream().filter(g -> g.label().equals(nisaLabel)).findFirst().orElseThrow();
        final var trip = result.goalProgress().stream().filter(g -> g.label().equals(tripLabel)).findFirst().orElseThrow();

        // NISA balance = 200k prior + 80k this month = 280k; pct = 280k/300k toward the amount target.
        assertThat(nisa.balance(), is(comparesEqualTo(new BigDecimal("280000"))));
        assertThat(nisa.target(), is(comparesEqualTo(new BigDecimal("300000"))));
        assertThat(nisa.pct(), is(comparesEqualTo(new BigDecimal("0.9333"))));
        assertThat(nisa.complete(), is(false));
        assertThat(nisa.savings(), is(true));

        // Trip is open: balance = 50k prior + 30k this month = 80k; no target, no pct.
        assertThat(trip.balance(), is(comparesEqualTo(new BigDecimal("80000"))));
        assertThat(trip.target(), is(nullValue()));
        assertThat(trip.pct(), is(nullValue()));
        assertThat(trip.savings(), is(false));

        // Overall savings balance sums only the savings-flagged goal's balance.
        assertThat(result.savingsBalance(), is(comparesEqualTo(new BigDecimal("280000"))));
    }

    @Test
    void shouldMarkGoalCompleteWhenBalanceReachesAmountTarget() {
        final var label = "Emergency-" + UUID.randomUUID();
        final var base = nextBaseMonth();

        // 500k already banked before this month; a 500k amount target is met with no further contribution.
        seedMonth(base, new SeedGoal(label, "500000", true));

        final var view = new BudgetMonthView(
                List.of(),
                List.of(),
                List.of(new BudgetMonthView.GoalView(label, BigDecimal.ZERO, "JPY",
                        new BudgetMonthView.TargetView(GoalTargetType.AMOUNT, new BigDecimal("500000"), null, null, null, null, null), true, null, false, null)),
                List.of(),
                List.of(new BudgetMonthView.CurrencyView("JPY", "¥")));

        final var result = QuarkusTransaction.requiringNew().call(() -> budgetService.compute(view, base.plusMonths(1)));

        final var goal = result.goalProgress().getFirst();
        assertThat(goal.balance(), is(comparesEqualTo(new BigDecimal("500000"))));
        assertThat(goal.pct(), is(comparesEqualTo(BigDecimal.ONE))); // clamped to 1
        assertThat(goal.complete(), is(true));
    }

    @Test
    void shouldExcludeClosedGoalFromMoneyOutButKeepBalanceAndActivity() {
        // A goal carried 200k across two prior months and is now closed this month with a 50k monthly
        // contribution still entered. Closed → it drops out of money-out (savings/non-savings totals)
        // but keeps its accumulated balance and shows in this month's activity.
        final var label = "Closed-" + UUID.randomUUID();
        final var base = nextBaseMonth();
        final var asOf = base.plusMonths(2);
        seedMonth(base, new SeedGoal(label, "100000", true));
        seedMonth(base.plusMonths(1), new SeedGoal(label, "100000", true));

        final var goal = new BudgetMonthView.GoalView(label, new BigDecimal("50000"), "JPY",
                new BudgetMonthView.TargetView(GoalTargetType.OPEN, null, null, null, null, null, null),
                true, null, true, asOf.toString());
        final var view = new BudgetMonthView(List.of(), List.of(), List.of(goal), List.of(),
                List.of(new BudgetMonthView.CurrencyView("JPY", "¥")));

        final var result = QuarkusTransaction.requiringNew().call(() -> budgetService.compute(view, asOf));

        // Closed goal contributes nothing to money-out.
        assertThat(result.savingsGoals(), is(comparesEqualTo(BigDecimal.ZERO)));
        assertThat(result.moneyOut(), is(comparesEqualTo(BigDecimal.ZERO)));

        // Its balance is still 200k (prior only; no live contribution), and it is excluded from the
        // overall savings balance once closed.
        final var progress = result.goalProgress().getFirst();
        assertThat(progress.balance(), is(comparesEqualTo(new BigDecimal("200000"))));
        assertThat(progress.closed(), is(true));
        assertThat(result.savingsBalance(), is(comparesEqualTo(BigDecimal.ZERO)));

        // It appears in this month's activity as a closure carrying its remaining balance.
        final var closure = result.activity().stream().filter(a -> a.kind().equals("closed")).findFirst().orElseThrow();
        assertThat(closure.label(), is(label));
        assertThat(closure.amount(), is(comparesEqualTo(new BigDecimal("200000"))));
    }

    @Test
    void shouldTrackTimeGoalProgressAndStopContributingOnceDuePasses() {
        // A 12-month TIME goal created in `base`, due the first of the next year, contributing 40k/mo.
        final var label = "Vacation-" + UUID.randomUUID();
        final var base = nextBaseMonth();
        seedMonth(base, new SeedGoal(label, "40000", false));

        // While the deadline is in the future the goal is active: it contributes and time progress
        // climbs. (Asserted at the midpoint, where progress sits strictly between 0 and 1.)
        final var atMidpoint = QuarkusTransaction.requiringNew().call(() ->
                budgetService.compute(timeGoalView(label, base.plusYears(1).atDay(1)), base.plusMonths(6)));
        final var mid = atMidpoint.goalProgress().getFirst();
        assertThat(mid.complete(), is(false));
        assertThat(mid.target(), is(nullValue()));                 // TIME has no money target
        assertThat(mid.pct(), is(greaterThan(BigDecimal.ZERO)));
        assertThat(mid.pct(), is(lessThan(BigDecimal.ONE)));
        assertThat(atMidpoint.nonSavingsGoals(), is(comparesEqualTo(new BigDecimal("40000")))); // contributes

        // Once `asOf` reaches the due date the goal is complete: full time progress and it stops
        // contributing to money-out, while its accumulated balance is retained.
        final var atDue = QuarkusTransaction.requiringNew().call(() ->
                budgetService.compute(timeGoalView(label, base.plusYears(1).atDay(1)), base.plusYears(1)));
        final var done = atDue.goalProgress().getFirst();
        assertThat(done.complete(), is(true));
        assertThat(done.pct(), is(comparesEqualTo(BigDecimal.ONE)));
        assertThat(atDue.nonSavingsGoals(), is(comparesEqualTo(BigDecimal.ZERO)));   // no longer contributing
        assertThat(atDue.moneyOut(), is(comparesEqualTo(BigDecimal.ZERO)));
        assertThat(done.balance(), is(comparesEqualTo(new BigDecimal("40000"))));    // prior contribution retained
    }

    @Test
    void shouldStopAmountGoalContributingOnceTargetReached() {
        // 500k already banked toward a 500k target; this month still enters a 50k contribution. The
        // target is already reached, so the goal is complete and the 50k drops out of money-out.
        final var label = "Fund-" + UUID.randomUUID();
        final var base = nextBaseMonth();
        seedMonth(base, new SeedGoal(label, "500000", false));

        final var view = new BudgetMonthView(List.of(), List.of(),
                List.of(new BudgetMonthView.GoalView(label, new BigDecimal("50000"), "JPY",
                        new BudgetMonthView.TargetView(GoalTargetType.AMOUNT, new BigDecimal("500000"), null, null, null, null, null),
                        false, null, false, null)),
                List.of(), List.of(new BudgetMonthView.CurrencyView("JPY", "¥")));

        final var result = QuarkusTransaction.requiringNew().call(() -> budgetService.compute(view, base.plusMonths(1)));

        final var goal = result.goalProgress().getFirst();
        assertThat(goal.complete(), is(true));
        assertThat(goal.balance(), is(comparesEqualTo(new BigDecimal("500000"))));   // no live contribution
        assertThat(result.nonSavingsGoals(), is(comparesEqualTo(BigDecimal.ZERO)));  // stopped contributing
        assertThat(result.moneyOut(), is(comparesEqualTo(BigDecimal.ZERO)));
    }

    @Test
    void shouldCaptureWithdrawalsAndClosuresInActivity() {
        // One goal withdraws this month; another is closed this month. Both surface in activity.
        final var withdrawnLabel = "Wd-" + UUID.randomUUID();
        final var closedLabel = "Cl-" + UUID.randomUUID();
        final var base = nextBaseMonth();
        final var asOf = base.plusMonths(1);
        seedMonth(base, new SeedGoal(withdrawnLabel, "100000", true), new SeedGoal(closedLabel, "100000", true));

        final var withdrawing = new BudgetMonthView.GoalView(withdrawnLabel, BigDecimal.ZERO, "JPY",
                new BudgetMonthView.TargetView(GoalTargetType.OPEN, null, null, null, null, null, null),
                true, new BigDecimal("30000"), false, null);
        final var closing = new BudgetMonthView.GoalView(closedLabel, BigDecimal.ZERO, "JPY",
                new BudgetMonthView.TargetView(GoalTargetType.OPEN, null, null, null, null, null, null),
                true, null, true, asOf.toString());
        final var view = new BudgetMonthView(List.of(), List.of(), List.of(withdrawing, closing), List.of(),
                List.of(new BudgetMonthView.CurrencyView("JPY", "¥")));

        final var result = QuarkusTransaction.requiringNew().call(() -> budgetService.compute(view, asOf));

        final var withdrawal = result.activity().stream().filter(a -> a.kind().equals("withdrawal")).findFirst().orElseThrow();
        assertThat(withdrawal.label(), is(withdrawnLabel));
        assertThat(withdrawal.amount(), is(comparesEqualTo(new BigDecimal("30000"))));

        final var closure = result.activity().stream().filter(a -> a.kind().equals("closed")).findFirst().orElseThrow();
        assertThat(closure.label(), is(closedLabel));
    }

    @Test
    void shouldReturnAnEmptyMonthWithCurrenciesWhenNoneSaved() {
        final var view = QuarkusTransaction.requiringNew().call(() -> budgetService.getMonth(YearMonth.of(2099, 12)));

        assertThat(view.salaries(), is(empty()));
        assertThat(view.expenses(), is(empty()));
        assertThat(view.goals(), is(empty()));
        assertThat(view.debts(), is(empty()));
    }

    @Test
    void shouldOverwriteAnExistingMonthOnSave() {
        final var first = new BudgetMonthView(List.of(), List.of(
                new BudgetMonthView.ExpenseView("Rent", new BigDecimal("100000"), "JPY", null)),
                List.of(), List.of(), List.of(new BudgetMonthView.CurrencyView("JPY", "¥")));
        final var second = new BudgetMonthView(List.of(), List.of(
                new BudgetMonthView.ExpenseView("Groceries", new BigDecimal("80000"), "JPY", null)),
                List.of(), List.of(), List.of(new BudgetMonthView.CurrencyView("JPY", "¥")));

        QuarkusTransaction.requiringNew().run(() -> budgetService.saveMonth(YearMonth.of(2040, 1), first, null));
        QuarkusTransaction.requiringNew().run(() -> budgetService.saveMonth(YearMonth.of(2040, 1), second, null));

        final var loaded = QuarkusTransaction.requiringNew().call(() -> budgetService.getMonth(YearMonth.of(2040, 1)));
        assertThat(loaded.expenses().stream().map(BudgetMonthView.ExpenseView::label).toList(), contains("Groceries"));
    }

    @Test
    void shouldTotalDebtPrepaymentAcrossThisYearsSavedMonths() {
        // A prepayment-flagged debt accumulates its prepayment across the year's saved months,
        // matched by name. Unique name + a far-future random year so the cross-month query sees only
        // this run's debts and the months never collide on the shared, reused test DB.
        final var name = "Mortgage-" + UUID.randomUUID();
        final var base = nextBaseMonth();
        final var asOf = base.plusMonths(2);

        // Two earlier saved months in the same year each record 100k of prepayment on this debt.
        seedPrepayMonth(base, name, "100000");
        seedPrepayMonth(base.plusMonths(1), name, "100000");

        // The month being planned records another 100k on the same debt, plus an unrelated debt with
        // no prepayment (which must not appear on the card).
        final var view = new BudgetMonthView(List.of(), List.of(), List.of(),
                List.of(
                        debtView(name, true, "100000"),
                        debtView("Car-" + UUID.randomUUID(), false, null)),
                List.of(new BudgetMonthView.CurrencyView("JPY", "¥")));

        final var result = QuarkusTransaction.requiringNew().call(() -> budgetService.compute(view, asOf));

        // Only the prepayment-flagged debt is listed, with its prepayment to date this year: 100k
        // (this month) + 200k (the two earlier saved months) = 300k, in its own (base) currency.
        assertThat(result.prepayYear(), hasSize(1));
        final var row = result.prepayYear().getFirst();
        assertThat(row.name(), is(name));
        assertThat(row.currency(), is("JPY"));
        assertThat(row.amount(), is(comparesEqualTo(new BigDecimal("300000"))));
        assertThat(row.amountBase(), is(comparesEqualTo(new BigDecimal("300000"))));
    }

    @Test
    void shouldExcludeDebtPrepaymentFromOtherYears() {
        // Prepayment recorded on the same debt in a different year must not count toward this year.
        final var name = "Loan-" + UUID.randomUUID();
        final var base = nextBaseMonth().withMonth(6);
        seedPrepayMonth(base.minusYears(1), name, "100000");

        final var view = new BudgetMonthView(List.of(), List.of(), List.of(),
                List.of(debtView(name, true, "100000")),
                List.of(new BudgetMonthView.CurrencyView("JPY", "¥")));

        final var result = QuarkusTransaction.requiringNew().call(() -> budgetService.compute(view, base));

        // Only this month's 100k counts; last year's 100k is in a different year and excluded.
        final var row = result.prepayYear().getFirst();
        assertThat(row.name(), is(name));
        assertThat(row.amountBase(), is(comparesEqualTo(new BigDecimal("100000"))));
    }

    @Test
    void shouldPersistTheCurrencyListAcrossSaveAndReload() {
        // Currencies are a global household list; saving a month writes the working list to
        // CurrencySetting so it survives a reload (regression: additions used to vanish on refresh).
        // No other test asserts the global currency list, so mutating it here stays isolated.
        final var key = nextBaseMonth();
        final var three = new BudgetMonthView(List.of(), List.of(), List.of(), List.of(),
                List.of(new BudgetMonthView.CurrencyView("JPY", "¥"),
                        new BudgetMonthView.CurrencyView("PHP", "₱"),
                        new BudgetMonthView.CurrencyView("USD", "$")));
        QuarkusTransaction.requiringNew().run(() -> budgetService.saveMonth(key, three, null));

        final var reloaded = QuarkusTransaction.requiringNew().call(() -> budgetService.getMonth(key));
        assertThat(reloaded.cur().stream().map(BudgetMonthView.CurrencyView::code).toList(), contains("JPY", "PHP", "USD"));
        assertThat(reloaded.cur().stream().map(BudgetMonthView.CurrencyView::symbol).toList(), contains("¥", "₱", "$"));

        // Saving again without USD drops it from the persisted list (removals persist too).
        final var two = new BudgetMonthView(List.of(), List.of(), List.of(), List.of(),
                List.of(new BudgetMonthView.CurrencyView("JPY", "¥"), new BudgetMonthView.CurrencyView("PHP", "₱")));
        QuarkusTransaction.requiringNew().run(() -> budgetService.saveMonth(key, two, null));

        final var afterRemoval = QuarkusTransaction.requiringNew().call(() -> budgetService.getMonth(key));
        assertThat(afterRemoval.cur().stream().map(BudgetMonthView.CurrencyView::code).toList(), contains("JPY", "PHP"));
    }

    private BudgetMonthView timeGoalView(String label, java.time.LocalDate dueDate) {
        final var goal = new BudgetMonthView.GoalView(label, new BigDecimal("40000"), "JPY",
                new BudgetMonthView.TargetView(GoalTargetType.TIME, null, null, null, dueDate, null, null),
                false, null, false, null);
        return new BudgetMonthView(List.of(), List.of(), List.of(goal), List.of(),
                List.of(new BudgetMonthView.CurrencyView("JPY", "¥")));
    }

    private record SeedGoal(String label, String amount, boolean savings) {
    }

    private void seedNisaGoal(YearMonth yearMonth, String label, String amount) {
        seedMonth(yearMonth, new SeedGoal(label, amount, true));
    }

    private void seedMonth(YearMonth yearMonth, SeedGoal... goals) {
        QuarkusTransaction.requiringNew().run(() -> {
            final var month = new BudgetMonth().setYearMonth(yearMonth).setBaseCurrency("JPY");
            for (var ordinal = 0; ordinal < goals.length; ordinal++) {
                final var goal = goals[ordinal];
                month.getGoals().add(new Goal().setBudgetMonth(month).setOrdinal(ordinal)
                        .setLabel(goal.label()).setAmount(new BigDecimal(goal.amount())).setCurrency("JPY").setSavings(goal.savings()));
            }

            budgetMonthRepository.insertWithSession(month);
        });
    }

    private BudgetMonthView.DebtView debtView(String name, boolean prepay, String prepayAmount) {
        return new BudgetMonthView.DebtView(name, new BigDecimal("5000000"), new BigDecimal("5"),
                new BigDecimal("40000"), 240, DebtRepriceMode.PAYMENT, "JPY", prepay,
                prepayAmount == null ? null : new BigDecimal(prepayAmount), "JPY", List.of());
    }

    private void seedPrepayMonth(YearMonth yearMonth, String debtName, String prepayAmount) {
        QuarkusTransaction.requiringNew().run(() -> {
            final var month = new BudgetMonth().setYearMonth(yearMonth).setBaseCurrency("JPY");
            month.getDebts().add(new Debt().setBudgetMonth(month).setOrdinal(0)
                    .setName(debtName).setPrincipal(new BigDecimal("5000000")).setAnnualRate(new BigDecimal("5"))
                    .setMonthly(new BigDecimal("40000")).setTermMonths(240).setRepriceMode(DebtRepriceMode.PAYMENT)
                    .setCurrency("JPY").setPrepay(true).setPrepayAmount(new BigDecimal(prepayAmount)).setPrepayCurrency("JPY"));
            budgetMonthRepository.insertWithSession(month);
        });
    }
}
