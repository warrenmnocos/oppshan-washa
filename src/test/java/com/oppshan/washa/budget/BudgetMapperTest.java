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

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

class BudgetMapperTest {

    private final BudgetMapper mapper = new BudgetMapper();

    private BudgetMonthView fullMonth() {
        final var component = new ComponentView("Basic salary", new BigDecimal("500000"), true, true, "base", false);
        final var bracket = new BracketView("taxable", BracketOp.GT, new BigDecimal("20833"), BracketType.FORMULA, null, "0.15*(taxable-20833)");
        final var deduction = new DeductionView("Withholding", DeductionType.BRACKETS, DeductionBase.TAXABLE, null, null, null, null,
                BigDecimal.ZERO, null, null, false, null, false, List.of(bracket));
        final var varBracket = new BracketView("gross", BracketOp.GT, BigDecimal.ZERO, BracketType.PCTGROSS, new BigDecimal("1"), null);
        final var variable = new VariableView("ti", "Taxable income", VariableType.FORMULA, DeductionBase.TAXABLE, null, null, null, null,
                BigDecimal.ZERO, "max(0, gross-100000)", false, List.of(varBracket));
        final var salary = new SalaryView("Alice", "JPY", "generic", List.of(component), List.of(deduction), List.of(variable));

        final var expense = new ExpenseView("Rent", new BigDecimal("150000"), "JPY", null);
        final var goal = new GoalView("Emergency", new BigDecimal("150000"), "JPY",
                new TargetView(GoalTargetType.RELATIVE, null, "all", new BigDecimal("6"), null, null, null), true, new BigDecimal("5000"), true, "2026-06");
        final var rateStep = new RateStepView(new BigDecimal("3"), new BigDecimal("5.75"));
        final var debt = new DebtView("Mortgage", new BigDecimal("5000000"), new BigDecimal("6.5"),
                new BigDecimal("38000"), 240, DebtRepriceMode.PAYMENT, "PHP", true, new BigDecimal("10000"), "PHP", List.of(rateStep));

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

        assertThat(entity.getYearMonth(), is(YearMonth.of(2026, 6)));
        assertThat(entity.getBaseCurrency(), is("JPY"));

        final var salary = view.salaries().getFirst();
        assertThat(salary.name(), is("Alice"));
        assertThat(salary.components().getFirst().basic(), is(true));
        assertThat(salary.deductions().getFirst().brackets(), is(hasSize(1)));
        assertThat(salary.variables().getFirst().var(), is("ti"));
        assertThat(salary.variables().getFirst().brackets().getFirst().type(), is(BracketType.PCTGROSS));

        assertThat(view.expenses().getFirst().label(), is("Rent"));
        assertThat(view.goals().getFirst().target().type(), is(GoalTargetType.RELATIVE));
        assertThat(view.goals().getFirst().withdrawal(), is(comparesEqualTo(new BigDecimal("5000"))));
        assertThat(view.goals().getFirst().closed(), is(true));
        assertThat(view.goals().getFirst().closedKey(), is("2026-06"));

        final var debt = view.debts().getFirst();
        assertThat(debt.annualRate(), is(comparesEqualTo(new BigDecimal("6.5"))));
        assertThat(debt.prepay(), is(true));
        assertThat(debt.rateSteps().getFirst().afterYears(), is(comparesEqualTo(new BigDecimal("3"))));

        assertThat(view.cur().stream().map(CurrencyView::code).toList(), contains("JPY", "PHP"));
    }

    @Test
    void shouldDefaultBaseCurrencyAndOpenTargetWhenAbsent() {
        final var goal = new GoalView("Open goal", new BigDecimal("100"), "JPY", null, false, BigDecimal.ZERO, false, null);
        final var view = new BudgetMonthView(List.of(), List.of(), List.of(goal), List.of(), List.of());

        final var entity = mapper.toEntity(YearMonth.of(2026, 7), view);

        assertThat(entity.getBaseCurrency(), is("JPY")); // default when cur[] empty
        assertThat(entity.getGoals().getFirst().getTargetType(), is(GoalTargetType.OPEN));
    }

    @Test
    void shouldRoundTripTimeTargetDueDateAndPeriod() {
        final var dueDate = java.time.LocalDate.of(2027, 1, 1);
        final var goal = new GoalView("Vacation", new BigDecimal("40000"), "JPY",
                new TargetView(GoalTargetType.TIME, null, null, null, dueDate, 12, "months"), false, BigDecimal.ZERO, false, null);
        final var view = new BudgetMonthView(List.of(), List.of(), List.of(goal), List.of(),
                List.of(new CurrencyView("JPY", "¥")));

        final var entity = mapper.toEntity(YearMonth.of(2026, 7), view);
        final var mapped = entity.getGoals().getFirst();
        assertThat(mapped.getTargetType(), is(GoalTargetType.TIME));
        assertThat(mapped.getTargetDueDate(), is(dueDate));
        assertThat(mapped.getTargetPeriodCount(), is(12));
        assertThat(mapped.getTargetPeriodUnit(), is("months"));

        final var back = mapper.toView(entity, List.of(new CurrencySetting().setCode("JPY").setOrdinal(0).setSymbol("¥")));
        assertThat(back.goals().getFirst().target().dueDate(), is(dueDate));
        assertThat(back.goals().getFirst().target().periodCount(), is(12));
        assertThat(back.goals().getFirst().target().unit(), is("months"));
    }
}
