package com.oppshan.washa.budget;

import com.oppshan.washa.budget.BudgetMonthView.BracketView;
import com.oppshan.washa.budget.BudgetMonthView.ComponentView;
import com.oppshan.washa.budget.BudgetMonthView.CurrencyView;
import com.oppshan.washa.budget.BudgetMonthView.DebtView;
import com.oppshan.washa.budget.BudgetMonthView.DeductionView;
import com.oppshan.washa.budget.BudgetMonthView.ExpenseView;
import com.oppshan.washa.budget.BudgetMonthView.GoalView;
import com.oppshan.washa.budget.BudgetMonthView.RateStepView;
import com.oppshan.washa.budget.BudgetMonthView.SalaryView;
import com.oppshan.washa.budget.BudgetMonthView.TargetView;
import com.oppshan.washa.budget.BudgetMonthView.VariableView;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class BudgetMapperTest {

    private final BudgetMapper mapper = new BudgetMapper();

    private BudgetMonthView fullMonth() {
        final var component = new ComponentView("Basic salary", new BigDecimal("500000"), true, true, "base", false);
        final var bracket = new BracketView("taxable", "gt", new BigDecimal("20833"), "formula", null, "0.15*(taxable-20833)");
        final var deduction = new DeductionView("Withholding", "brackets", "taxable", null, null, null, null,
                BigDecimal.ZERO, null, null, false, null, false, List.of(bracket));
        final var varBracket = new BracketView("gross", "gt", BigDecimal.ZERO, "pctgross", new BigDecimal("1"), null);
        final var variable = new VariableView("ti", "Taxable income", "formula", "taxable", null, null, null, null,
                BigDecimal.ZERO, "max(0, gross-100000)", false, List.of(varBracket));
        final var salary = new SalaryView("Alice", "JPY", "generic", List.of(component), List.of(deduction), List.of(variable));

        final var expense = new ExpenseView("Rent", new BigDecimal("150000"), "JPY", null);
        final var goal = new GoalView("Emergency", new BigDecimal("150000"), "JPY",
                new TargetView("relative", null, "all", new BigDecimal("6")), true, new BigDecimal("5000"));
        final var rateStep = new RateStepView(new BigDecimal("3"), new BigDecimal("5.75"));
        final var debt = new DebtView("Mortgage", new BigDecimal("5000000"), new BigDecimal("6.5"),
                new BigDecimal("38000"), 240, "payment", "PHP", true, new BigDecimal("10000"), "PHP", List.of(rateStep));

        return new BudgetMonthView(List.of(salary), List.of(expense), List.of(goal), List.of(debt),
                List.of(new CurrencyView("JPY", "¥"), new CurrencyView("PHP", "₱")));
    }

    @Test
    void shouldRoundTripAFullMonthThroughEntityAndBack() {
        final var entity = mapper.toEntity(YearMonth.of(2026, 6), fullMonth());
        final var currencies = List.of(
                new CurrencySetting().setCode("JPY").setOrdinal(0).setSymbol("¥"),
                new CurrencySetting().setCode("PHP").setOrdinal(1).setSymbol("₱"));
        final var view = mapper.toView(entity, currencies);

        assertThat(entity.getYearMonth()).isEqualTo(YearMonth.of(2026, 6));
        assertThat(entity.getBaseCurrency()).isEqualTo("JPY");

        final var salary = view.salaries().get(0);
        assertThat(salary.name()).isEqualTo("Alice");
        assertThat(salary.components().get(0).basic()).isTrue();
        assertThat(salary.deductions().get(0).brackets()).hasSize(1);
        assertThat(salary.variables().get(0).var()).isEqualTo("ti");
        assertThat(salary.variables().get(0).brackets().get(0).type()).isEqualTo("pctgross");

        assertThat(view.expenses().get(0).label()).isEqualTo("Rent");
        assertThat(view.goals().get(0).target().type()).isEqualTo("relative");
        assertThat(view.goals().get(0).withdrawal()).isEqualByComparingTo("5000");

        final var debt = view.debts().get(0);
        assertThat(debt.annualRate()).isEqualByComparingTo("6.5");
        assertThat(debt.prepay()).isTrue();
        assertThat(debt.rateSteps().get(0).afterYears()).isEqualByComparingTo("3");

        assertThat(view.cur()).extracting(CurrencyView::code).containsExactly("JPY", "PHP");
    }

    @Test
    void shouldDefaultBaseCurrencyAndOpenTargetWhenAbsent() {
        final var goal = new GoalView("Open goal", new BigDecimal("100"), "JPY", null, false, BigDecimal.ZERO);
        final var view = new BudgetMonthView(List.of(), List.of(), List.of(goal), List.of(), List.of());

        final var entity = mapper.toEntity(YearMonth.of(2026, 7), view);

        assertThat(entity.getBaseCurrency()).isEqualTo("JPY"); // default when cur[] empty
        assertThat(entity.getGoals().get(0).getTargetType()).isEqualTo(Goal.TargetType.open);
    }
}
