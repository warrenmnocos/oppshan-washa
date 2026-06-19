package com.oppshan.washa.budget;

import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.YearMonth;

import static org.assertj.core.api.Assertions.assertThat;

@QuarkusTest
class BudgetMonthRepositoryTest {

    @Inject
    EntityManager entityManager;

    @Inject
    BudgetMonthRepository repository;

    @Test
    void shouldPersistFullMonthGraphViaCascadeAndReload() {
        final var monthId = QuarkusTransaction.requiringNew().call(() -> {
            final var month = new BudgetMonth()
                    .setYearMonth(YearMonth.of(2026, 6))
                    .setBaseCurrency("JPY")
                    .setFxRate(new BigDecimal("0.3900"));

            final var income = new Income().setBudgetMonth(month).setOrdinal(0)
                    .setName("Alice").setCurrency("JPY").setEngine("generic");
            income.getComponents().add(new IncomeComponent().setIncome(income).setOrdinal(0)
                    .setLabel("Basic salary").setAmount(new BigDecimal("500000")).setTaxable(true).setBasic(true));
            final var pension = new IncomeDeduction().setIncome(income).setOrdinal(0)
                    .setLabel("Employees' pension").setKind("pct").setBase("gross")
                    .setRate(new BigDecimal("9.15")).setCap(new BigDecimal("59475")).setPretax(true);
            income.getDeductions().add(pension);
            final var taxable = new IncomeVariable().setIncome(income).setOrdinal(0)
                    .setVarName("ti").setKind("formula").setExpr("max(0, gross - 100000)");
            income.getVariables().add(taxable);
            month.getIncomes().add(income);

            month.getExpenses().add(new Expense().setBudgetMonth(month).setOrdinal(0)
                    .setLabel("Rent").setAmount(new BigDecimal("150000")).setCurrency("JPY"));

            month.getGoals().add(new Goal().setBudgetMonth(month).setOrdinal(0)
                    .setLabel("Emergency fund").setAmount(new BigDecimal("150000")).setCurrency("JPY")
                    .setTargetType(Goal.TargetType.relative).setTargetBase("all")
                    .setTargetMult(new BigDecimal("6")).setSavings(true));

            final var debt = new Debt().setBudgetMonth(month).setOrdinal(0).setName("Cebu mortgage")
                    .setPrincipal(new BigDecimal("5000000")).setAnnualRate(new BigDecimal("6.5"))
                    .setMonthly(new BigDecimal("38000")).setTermMonths(240).setRepriceMode("payment")
                    .setCurrency("PHP").setPrepay(true).setPrepayAmount(new BigDecimal("10000"))
                    .setPrepayCurrency("PHP");
            debt.getRateSteps().add(new DebtRateStep().setDebt(debt).setOrdinal(0)
                    .setAfterYears(new BigDecimal("3")).setRate(new BigDecimal("5.75")));
            month.getDebts().add(debt);

            // Bracket under the pension deduction (additive bracket model).
            pension.getBrackets().add(new SalaryBracket().setDeduction(pension).setOrdinal(0)
                    .setVarName("taxable").setOp("gt").setVal(new BigDecimal("20833.33"))
                    .setType("formula").setExpr("0.15*(taxable-20833.33)"));

            entityManager.persist(month); // cascade ALL persists the whole graph
            return month.getUuid();
        });

        QuarkusTransaction.requiringNew().run(() -> {
            // The YearMonth converter round-trips through the CHAR(7) column.
            assertThat(repository.findByYearMonth(YearMonth.of(2026, 6))).isPresent();

            final var loaded = entityManager.find(BudgetMonth.class, monthId);
            assertThat(loaded.getYearMonth()).isEqualTo(YearMonth.of(2026, 6));
            assertThat(loaded.getIncomes()).hasSize(1);
            final var income = loaded.getIncomes().get(0);
            assertThat(income.getComponents()).hasSize(1);
            assertThat(income.getDeductions()).hasSize(1);
            assertThat(income.getVariables()).hasSize(1);
            assertThat(income.getDeductions().get(0).getBrackets()).hasSize(1);
            assertThat(loaded.getExpenses()).extracting(Expense::getLabel).containsExactly("Rent");
            assertThat(loaded.getGoals().get(0).getTargetType()).isEqualTo(Goal.TargetType.relative);
            assertThat(loaded.getDebts().get(0).isPrepay()).isTrue();
            assertThat(loaded.getDebts().get(0).getRateSteps().get(0).getRate())
                    .isEqualByComparingTo("5.75");
            // created_at / last_modified_at (@Version) populated by Hibernate.
            assertThat(loaded.getCreatedAt()).isNotNull();
            assertThat(loaded.getLastModifiedAt()).isNotNull();
        });
    }
}
