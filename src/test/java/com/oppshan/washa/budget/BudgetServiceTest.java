package com.oppshan.washa.budget;

import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.YearMonth;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

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
        QuarkusTransaction.requiringNew().run(() ->
                fxRateRepository.insertWithSession(new FxRate()
                        .setId(new FxRateId("JPY", "PHP"))
                        .setRate(new BigDecimal("0.36"))
                        .setCapturedAt(Instant.now())));

        // In-memory month (no deductions → net == gross): 100,000 JPY + 360 PHP (== 1,000 JPY).
        final var month = new BudgetMonth().setYearMonth(YearMonth.of(2026, 6)).setBaseCurrency("JPY");
        month.getIncomes().add(simpleSalary(month, "Alice", "JPY", "100000"));
        month.getIncomes().add(simpleSalary(month, "Bob", "PHP", "360"));

        final var combinedNet = QuarkusTransaction.requiringNew().call(() -> budgetService.combinedNet(month));
        final var tithe = QuarkusTransaction.requiringNew().call(() -> budgetService.tithe(month));

        assertThat(combinedNet).isEqualByComparingTo("101000");
        assertThat(tithe).isEqualByComparingTo("10100");
    }

    @Test
    void shouldSumGoalContributionsAcrossPriorMonths() {
        // Distinct months so the shared (committed) test database does not collide with other tests.
        seedNisaGoal(YearMonth.of(2030, 4), "100000");
        seedNisaGoal(YearMonth.of(2030, 5), "100000");
        seedNisaGoal(YearMonth.of(2030, 6), "100000");

        final var prior = QuarkusTransaction.requiringNew().call(() ->
                budgetService.cumulativeGoalProgressBefore("NISA", "JPY", YearMonth.of(2030, 6)));

        assertThat(prior).isEqualByComparingTo("200000"); // April + May, not June
    }

    @Test
    void shouldReturnAnEmptyMonthWithCurrenciesWhenNoneSaved() {
        final var view = QuarkusTransaction.requiringNew().call(() -> budgetService.getMonth(YearMonth.of(2099, 12)));

        assertThat(view.salaries()).isEmpty();
        assertThat(view.expenses()).isEmpty();
        assertThat(view.goals()).isEmpty();
        assertThat(view.debts()).isEmpty();
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
        assertThat(loaded.expenses()).extracting(BudgetMonthView.ExpenseView::label).containsExactly("Groceries");
    }

    private void seedNisaGoal(YearMonth yearMonth, String amount) {
        QuarkusTransaction.requiringNew().run(() -> {
            final var month = new BudgetMonth().setYearMonth(yearMonth).setBaseCurrency("JPY");
            month.getGoals().add(new Goal().setBudgetMonth(month).setOrdinal(0)
                    .setLabel("NISA").setAmount(new BigDecimal(amount)).setCurrency("JPY").setSavings(true));
            budgetMonthRepository.insertWithSession(month);
        });
    }
}
