package com.oppshan.washa.budget;

import com.oppshan.washa.budget.BudgetMonthView.BracketView;
import com.oppshan.washa.budget.BudgetMonthView.ComponentView;
import com.oppshan.washa.budget.BudgetMonthView.DeductionView;
import com.oppshan.washa.budget.BudgetMonthView.SalaryView;
import com.oppshan.washa.budget.BudgetMonthView.VariableView;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;

/** Maps a salary view with deductions, variables, and brackets to a preset entity and back. */
class SalaryPresetMapperTest {

    private final SalaryPresetMapper mapper = new SalaryPresetMapper();

    @Test
    void shouldMapDeductionVariableAndBracketTreesToTheEntityAndBack() {
        final var bracket = new BracketView("taxable", BracketOp.GT, new BigDecimal("0"), BracketType.FIXED, new BigDecimal("500"), null);
        final var component = new ComponentView("Basic", new BigDecimal("100000"), true, true, null, false);
        final var deduction = new DeductionView("Tax", DeductionType.BRACKETS, DeductionBase.TAXABLE, null,
                null, null, null, null, null, null, false, null, false, List.of(bracket));
        final var variable = new VariableView("bonus", "Bonus", VariableType.PCT, DeductionBase.GROSS, null,
                new BigDecimal("10"), null, null, null, null, false, List.of(bracket));
        // null engine exercises the "generic" fallback
        final var salary = new SalaryView("Custom", "PHP", null, List.of(component), List.of(deduction), List.of(variable));

        final var entity = mapper.toEntity("Custom", false, salary);

        assertThat(entity.getEngine(), is("generic"));
        assertThat(entity.getDeductions().getFirst().getBrackets(), hasSize(1));
        assertThat(entity.getVariables(), hasSize(1));
        assertThat(entity.getVariables().getFirst().getBrackets(), hasSize(1));

        // back to a view exercises the variable view mapping
        final var view = mapper.toView(entity);
        assertThat(view.salary().variables(), hasSize(1));
        assertThat(view.salary().variables().getFirst().var(), is("bonus"));
    }

    @Test
    void shouldTolerateNullCollections() {
        final var salary = new SalaryView("Blank", "PHP", "generic", null, null, null);

        final var entity = mapper.toEntity("Blank", true, salary);

        assertThat(entity.getComponents(), is(empty()));
        assertThat(entity.getDeductions(), is(empty()));
        assertThat(entity.getVariables(), is(empty()));
    }
}
