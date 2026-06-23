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
            // Idempotent: the JPY→PHP rate may already exist in the shared, reused test DB.
            if (fxRateRepository.findById(new FxRateId("JPY", "PHP")).isEmpty()) {
                fxRateRepository.insertWithSession(new FxRate()
                        .setId(new FxRateId("JPY", "PHP"))
                        .setRate(new BigDecimal("0.36"))
                        .setCapturedAt(Instant.now()));
            }
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
        final var base = YearMonth.of(ThreadLocalRandom.current().nextInt(3000, 9000), 1);
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
                                new BudgetMonthView.TargetView("open", null, null, null), true, null),
                        new BudgetMonthView.GoalView("Trip", new BigDecimal("30000"), "JPY",
                                new BudgetMonthView.TargetView("open", null, null, null), false, null)),
                List.of(new BudgetMonthView.DebtView("Loan", new BigDecimal("5000000"), new BigDecimal("5"),
                        new BigDecimal("40000"), 240, "payment", "JPY", true, new BigDecimal("10000"), "JPY", List.of())),
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

    private void seedNisaGoal(YearMonth yearMonth, String label, String amount) {
        QuarkusTransaction.requiringNew().run(() -> {
            final var month = new BudgetMonth().setYearMonth(yearMonth).setBaseCurrency("JPY");
            month.getGoals().add(new Goal().setBudgetMonth(month).setOrdinal(0)
                    .setLabel(label).setAmount(new BigDecimal(amount)).setCurrency("JPY").setSavings(true));
            budgetMonthRepository.insertWithSession(month);
        });
    }
}
