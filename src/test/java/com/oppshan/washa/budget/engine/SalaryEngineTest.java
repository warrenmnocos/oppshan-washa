package com.oppshan.washa.budget.engine;

import com.oppshan.washa.budget.Income;
import com.oppshan.washa.budget.IncomeComponent;
import com.oppshan.washa.budget.IncomeDeduction;
import com.oppshan.washa.budget.SalaryBracket;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class SalaryEngineTest {

    private final SalaryEngine engine = new SalaryEngine();

    private Income salaryWith(BigDecimal basicAmount, boolean flagBasic) {
        final var income = new Income().setName("Example").setCurrency("JPY").setEngine("generic");
        income.getComponents().add(new IncomeComponent().setIncome(income).setOrdinal(0)
                .setLabel("Basic salary").setAmount(basicAmount).setTaxable(true).setBasic(flagBasic));
        return income;
    }

    private IncomeDeduction deduction(Income income, int ordinal, String label, String kind) {
        final var deduction = new IncomeDeduction().setIncome(income).setOrdinal(ordinal)
                .setLabel(label).setKind(kind);
        income.getDeductions().add(deduction);
        return deduction;
    }

    @Test
    void shouldSubtractFixedDeductionFromGross() {
        final var income = salaryWith(new BigDecimal("100000"), true);
        deduction(income, 0, "Union fee", "fixed").setAmount(new BigDecimal("5000"));

        final var breakdown = engine.compute(income);

        assertThat(breakdown.gross()).isEqualByComparingTo("100000");
        assertThat(breakdown.basic()).isEqualByComparingTo("100000");
        assertThat(breakdown.lines()).singleElement()
                .satisfies(line -> assertThat(line.amount()).isEqualByComparingTo("5000"));
        assertThat(breakdown.net()).isEqualByComparingTo("95000");
    }

    @Test
    void shouldComputePercentageDeductionOnGross() {
        final var income = salaryWith(new BigDecimal("100000"), true);
        deduction(income, 0, "Pension", "pct").setBase("gross").setRate(new BigDecimal("10")).setPretax(true);

        final var breakdown = engine.compute(income);

        assertThat(breakdown.lines().get(0).amount()).isEqualByComparingTo("10000");
        assertThat(breakdown.net()).isEqualByComparingTo("90000");
    }

    @Test
    void shouldCapADeductionAtItsCeiling() {
        final var income = salaryWith(new BigDecimal("100000"), true);
        deduction(income, 0, "Capped", "pct").setBase("gross").setRate(new BigDecimal("10"))
                .setCap(new BigDecimal("5000"));

        assertThat(engine.compute(income).lines().get(0).amount()).isEqualByComparingTo("5000");
    }

    @Test
    void shouldFloorADeductionAtItsMinimum() {
        final var income = salaryWith(new BigDecimal("100000"), true);
        deduction(income, 0, "Floored", "pct").setBase("gross").setRate(new BigDecimal("1"))
                .setFloorAmount(new BigDecimal("2000"));

        assertThat(engine.compute(income).lines().get(0).amount()).isEqualByComparingTo("2000");
    }

    @Test
    void shouldFallBackBasicToGrossWhenNoComponentIsFlaggedBasic() {
        final var income = salaryWith(new BigDecimal("100000"), false);
        deduction(income, 0, "On basic", "pct").setBase("basic").setRate(new BigDecimal("10"));

        final var breakdown = engine.compute(income);

        assertThat(breakdown.basic()).isEqualByComparingTo("100000");
        assertThat(breakdown.lines().get(0).amount()).isEqualByComparingTo("10000");
    }

    @Test
    void shouldReduceTaxableForLaterDeductionsViaPretaxAccumulation() {
        final var income = salaryWith(new BigDecimal("100000"), true);
        // Pretax 10% of gross (10,000) lowers taxable from 100,000 to 90,000 ...
        deduction(income, 0, "Pension", "pct").setBase("gross").setRate(new BigDecimal("10")).setPretax(true);
        // ... so the income tax (10% of taxable) is 9,000, not 10,000.
        deduction(income, 1, "Income tax", "formula").setExpr("taxable * 0.1");

        final var breakdown = engine.compute(income);

        assertThat(breakdown.lines().get(0).amount()).isEqualByComparingTo("10000");
        assertThat(breakdown.lines().get(1).amount()).isEqualByComparingTo("9000");
        assertThat(breakdown.net()).isEqualByComparingTo("81000");
    }

    @Test
    void shouldSumAdditiveBracketRows() {
        final var income = salaryWith(new BigDecimal("100000"), true);
        final var tax = deduction(income, 0, "Withholding", "brackets");
        // taxable = 100,000. Row1: +0.1*(taxable-50000)=5000; Row2: +0.05*(taxable-80000)=1000.
        tax.getBrackets().add(new SalaryBracket().setDeduction(tax).setOrdinal(0)
                .setVarName("taxable").setOp("gt").setVal(new BigDecimal("50000"))
                .setType("formula").setExpr("0.1*(taxable-50000)"));
        tax.getBrackets().add(new SalaryBracket().setDeduction(tax).setOrdinal(1)
                .setVarName("taxable").setOp("gt").setVal(new BigDecimal("80000"))
                .setType("formula").setExpr("0.05*(taxable-80000)"));

        assertThat(engine.compute(income).lines().get(0).amount()).isEqualByComparingTo("6000");
    }
}
