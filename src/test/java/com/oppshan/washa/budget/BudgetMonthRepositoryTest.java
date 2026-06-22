package com.oppshan.washa.budget;

import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.YearMonth;

import static org.assertj.core.api.Assertions.assertThat;

@QuarkusTest
class BudgetMonthRepositoryTest {

    @Inject
    BudgetMonthRepository repository;

    @Test
    void shouldPersistFullMonthGraphViaCascade() {
        QuarkusTransaction.requiringNew().run(() -> {
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
            income.getVariables().add(new IncomeVariable().setIncome(income).setOrdinal(0)
                    .setVarName("ti").setKind("formula").setExpr("max(0, gross - 100000)"));
            month.getIncomes().add(income);

            month.getExpenses().add(new Expense().setBudgetMonth(month).setOrdinal(0)
                    .setLabel("Rent").setAmount(new BigDecimal("150000")).setCurrency("JPY"));

            month.getGoals().add(new Goal().setBudgetMonth(month).setOrdinal(0)
                    .setLabel("Emergency fund").setAmount(new BigDecimal("150000")).setCurrency("JPY")
                    .setTargetType(Goal.TargetType.relative).setTargetBase("all")
                    .setTargetMult(new BigDecimal("6")).setSavings(true));

            final var debt = new Debt()
                    .setBudgetMonth(month)
                    .setOrdinal(0)
                    .setName("Home mortgage")
                    .setPrincipal(new BigDecimal("5000000"))
                    .setAnnualRate(new BigDecimal("6.5"))
                    .setMonthly(new BigDecimal("38000"))
                    .setTermMonths(240)
                    .setRepriceMode("payment")
                    .setCurrency("PHP")
                    .setPrepay(true)
                    .setPrepayAmount(new BigDecimal("10000"))
                    .setPrepayCurrency("PHP");
            debt.getRateSteps().add(new DebtRateStep().setDebt(debt).setOrdinal(0)
                    .setAfterYears(new BigDecimal("3")).setRate(new BigDecimal("5.75")));
            month.getDebts().add(debt);

            // Bracket under the pension deduction (additive bracket model).
            pension.getBrackets().add(new SalaryBracket().setDeduction(pension).setOrdinal(0)
                    .setVarName("taxable").setOp("gt").setVal(new BigDecimal("20833.33"))
                    .setType("formula").setExpr("0.15*(taxable-20833.33)"));

            // Cascade ALL persists the whole graph; flush forces every INSERT (FK/not-null/check
            // constraints + @CreationTimestamp + @Version) to execute now.
            repository.insertWithSession(month);
            repository.flushWithSession();

            assertThat(month.getUuid()).isNotNull();
            assertThat(month.getCreatedAt()).isNotNull();
            assertThat(month.getLastModifiedAt()).isNotNull();
            assertThat(income.getUuid()).isNotNull();
            assertThat(pension.getBrackets().getFirst().getUuid()).isNotNull();
            assertThat(debt.getRateSteps().getFirst().getUuid()).isNotNull();
        });

        // Reload in a fresh transaction: the YearMonth converter round-trips through CHAR(7).
        QuarkusTransaction.requiringNew().run(() ->
                assertThat(repository.findByYearMonth(YearMonth.of(2026, 6))).isPresent());
    }
}
