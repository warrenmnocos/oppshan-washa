package com.oppshan.washa.budget.engine;

import com.oppshan.washa.budget.BracketOp;
import com.oppshan.washa.budget.BracketType;
import com.oppshan.washa.budget.DeductionBase;
import com.oppshan.washa.budget.DeductionType;
import com.oppshan.washa.budget.Income;
import com.oppshan.washa.budget.IncomeComponent;
import com.oppshan.washa.budget.IncomeDeduction;
import com.oppshan.washa.budget.IncomeVariable;
import com.oppshan.washa.budget.SalaryBracket;
import com.oppshan.washa.budget.VariableType;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

class SalaryEngineTest {

    private final SalaryEngine engine = new SalaryEngine();

    private Income salaryWith(BigDecimal basicAmount, boolean flagBasic) {
        final var income = new Income().setName("Example").setCurrency("JPY").setEngine("generic");
        income.getComponents().add(new IncomeComponent().setIncome(income).setOrdinal(0)
                .setLabel("Basic salary").setAmount(basicAmount).setTaxable(true).setBasic(flagBasic));
        return income;
    }

    private IncomeDeduction deduction(Income income, int ordinal, String label, DeductionType type) {
        final var deduction = new IncomeDeduction().setIncome(income).setOrdinal(ordinal)
                .setLabel(label).setType(type);
        income.getDeductions().add(deduction);
        return deduction;
    }

    @Test
    void shouldSubtractFixedDeductionFromGross() {
        final var income = salaryWith(new BigDecimal("100000"), true);
        deduction(income, 0, "Union fee", DeductionType.FIXED).setAmount(new BigDecimal("5000"));

        final var breakdown = engine.compute(income);

        assertThat(breakdown.gross(), is(comparesEqualTo(new BigDecimal("100000"))));
        assertThat(breakdown.basic(), is(comparesEqualTo(new BigDecimal("100000"))));
        assertThat(breakdown.lines(), is(hasSize(1)));
        assertThat(breakdown.lines().getFirst().amount(), is(comparesEqualTo(new BigDecimal("5000"))));
        assertThat(breakdown.net(), is(comparesEqualTo(new BigDecimal("95000"))));
    }

    @Test
    void shouldComputePercentageDeductionOnGross() {
        final var income = salaryWith(new BigDecimal("100000"), true);
        deduction(income, 0, "Pension", DeductionType.PCT).setBase(DeductionBase.GROSS).setRate(new BigDecimal("10")).setPretax(true);

        final var breakdown = engine.compute(income);

        assertThat(breakdown.lines().getFirst().amount(), is(comparesEqualTo(new BigDecimal("10000"))));
        assertThat(breakdown.net(), is(comparesEqualTo(new BigDecimal("90000"))));
    }

    @Test
    void shouldCapADeductionAtItsCeiling() {
        final var income = salaryWith(new BigDecimal("100000"), true);
        deduction(income, 0, "Capped", DeductionType.PCT).setBase(DeductionBase.GROSS).setRate(new BigDecimal("10"))
                .setCap(new BigDecimal("5000"));

        assertThat(engine.compute(income).lines().getFirst().amount(), is(comparesEqualTo(new BigDecimal("5000"))));
    }

    @Test
    void shouldFloorADeductionAtItsMinimum() {
        final var income = salaryWith(new BigDecimal("100000"), true);
        deduction(income, 0, "Floored", DeductionType.PCT).setBase(DeductionBase.GROSS).setRate(new BigDecimal("1"))
                .setFloorAmount(new BigDecimal("2000"));

        assertThat(engine.compute(income).lines().getFirst().amount(), is(comparesEqualTo(new BigDecimal("2000"))));
    }

    @Test
    void shouldFallBackBasicToGrossWhenNoComponentIsFlaggedBasic() {
        final var income = salaryWith(new BigDecimal("100000"), false);
        deduction(income, 0, "On basic", DeductionType.PCT).setBase(DeductionBase.BASIC).setRate(new BigDecimal("10"));

        final var breakdown = engine.compute(income);

        assertThat(breakdown.basic(), is(comparesEqualTo(new BigDecimal("100000"))));
        assertThat(breakdown.lines().getFirst().amount(), is(comparesEqualTo(new BigDecimal("10000"))));
    }

    @Test
    void shouldReduceTaxableForLaterDeductionsViaPretaxAccumulation() {
        final var income = salaryWith(new BigDecimal("100000"), true);
        // Pretax 10% of gross (10,000) lowers taxable from 100,000 to 90,000 ...
        deduction(income, 0, "Pension", DeductionType.PCT).setBase(DeductionBase.GROSS).setRate(new BigDecimal("10")).setPretax(true);
        // ... so the income tax (10% of taxable) is 9,000, not 10,000.
        deduction(income, 1, "Income tax", DeductionType.FORMULA).setExpr("taxable * 0.1");

        final var breakdown = engine.compute(income);

        assertThat(breakdown.lines().getFirst().amount(), is(comparesEqualTo(new BigDecimal("10000"))));
        assertThat(breakdown.lines().get(1).amount(), is(comparesEqualTo(new BigDecimal("9000"))));
        assertThat(breakdown.net(), is(comparesEqualTo(new BigDecimal("81000"))));
    }

    @Test
    void shouldSumAdditiveBracketRows() {
        final var income = salaryWith(new BigDecimal("100000"), true);
        final var tax = deduction(income, 0, "Withholding", DeductionType.BRACKETS);
        // taxable = 100,000. Row1: +0.1*(taxable-50000)=5000; Row2: +0.05*(taxable-80000)=1000.
        tax.getBrackets().add(new SalaryBracket().setDeduction(tax).setOrdinal(0)
                .setVarName("taxable").setOp(BracketOp.GT).setVal(new BigDecimal("50000"))
                .setType(BracketType.FORMULA).setExpr("0.1*(taxable-50000)"));
        tax.getBrackets().add(new SalaryBracket().setDeduction(tax).setOrdinal(1)
                .setVarName("taxable").setOp(BracketOp.GT).setVal(new BigDecimal("80000"))
                .setType(BracketType.FORMULA).setExpr("0.05*(taxable-80000)"));

        assertThat(engine.compute(income).lines().getFirst().amount(), is(comparesEqualTo(new BigDecimal("6000"))));
    }

    @Test
    void shouldComputeVariableThenUseItAsAPercentageBase() {
        final var income = salaryWith(new BigDecimal("100000"), true);
        // Variable 'half' = 50% of gross = 50,000, then a deduction of 10% of that var = 5,000.
        income.getVariables().add(new IncomeVariable().setIncome(income).setOrdinal(0)
                .setVarName("half").setType(VariableType.PCT).setBase(DeductionBase.GROSS).setRate(new BigDecimal("50")));
        deduction(income, 0, "On var", DeductionType.PCT).setBase(DeductionBase.VAR).setBaseVar("half").setRate(new BigDecimal("10"));

        assertThat(engine.compute(income).lines().getFirst().amount(), is(comparesEqualTo(new BigDecimal("5000"))));
    }

    @Test
    void shouldSupportPctGrossAndPctBasicBracketContributions() {
        final var income = salaryWith(new BigDecimal("100000"), true);
        final var levy = deduction(income, 0, "Levy", DeductionType.BRACKETS);
        // gross>0 → 1% of gross (1000); basic>0 → 2% of basic (2000). Sum 3000.
        levy.getBrackets().add(new SalaryBracket().setDeduction(levy).setOrdinal(0)
                .setVarName("gross").setOp(BracketOp.GT).setVal(BigDecimal.ZERO)
                .setType(BracketType.PCTGROSS).setRate(new BigDecimal("1")));
        levy.getBrackets().add(new SalaryBracket().setDeduction(levy).setOrdinal(1)
                .setVarName("basic").setOp(BracketOp.GT).setVal(BigDecimal.ZERO)
                .setType(BracketType.PCTBASIC).setRate(new BigDecimal("2")));

        assertThat(engine.compute(income).lines().getFirst().amount(), is(comparesEqualTo(new BigDecimal("3000"))));
    }

    @Test
    void shouldHonourEachComparisonOperatorInBrackets() {
        final var income = salaryWith(new BigDecimal("100"), true);
        final var step = deduction(income, 0, "Ops", DeductionType.BRACKETS);
        step.getBrackets().add(bracket(step, 0, "gross", BracketOp.GTE, "100", "10")); // 100>=100 → +10
        step.getBrackets().add(bracket(step, 1, "gross", BracketOp.LTE, "100", "10")); // 100<=100 → +10
        step.getBrackets().add(bracket(step, 2, "gross", BracketOp.EQ, "100", "10"));  // 100==100 → +10
        step.getBrackets().add(bracket(step, 3, "gross", BracketOp.LT, "100", "10"));  // 100<100  → skip
        step.getBrackets().add(bracket(step, 4, "gross", BracketOp.GT, "100", "10"));  // 100>100  → skip

        assertThat(engine.compute(income).lines().getFirst().amount(), is(comparesEqualTo(new BigDecimal("30"))));
    }

    private SalaryBracket bracket(IncomeDeduction parent, int ordinal, String var, BracketOp op,
                                  String val, String fixedRate) {
        return new SalaryBracket()
                .setDeduction(parent)
                .setOrdinal(ordinal)
                .setVarName(var)
                .setOp(op)
                .setVal(new BigDecimal(val))
                .setType(BracketType.FIXED)
                .setRate(new BigDecimal(fixedRate));
    }
}
