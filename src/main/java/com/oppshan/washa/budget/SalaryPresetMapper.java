package com.oppshan.washa.budget;

import com.oppshan.washa.budget.BudgetMonthView.BracketView;
import com.oppshan.washa.budget.BudgetMonthView.ComponentView;
import com.oppshan.washa.budget.BudgetMonthView.DeductionView;
import com.oppshan.washa.budget.BudgetMonthView.SalaryView;
import com.oppshan.washa.budget.BudgetMonthView.VariableView;
import jakarta.enterprise.context.ApplicationScoped;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.function.ObjIntConsumer;
import java.util.function.ToIntFunction;
import java.util.stream.Stream;

/**
 * Maps a {@link SalaryPreset} entity graph to/from the export-shaped {@link SalaryView}: the same
 * payload {@link BudgetMapper} produces for a month's income, so a preset reuses that shape unchanged.
 * List position becomes {@code ordinal} on the way in; entities are ordered by {@code ordinal} on the
 * way out. Back-references are wired so a cascade persist saves the whole graph.
 */
@ApplicationScoped
public class SalaryPresetMapper {

    /**
     * Builds the preset DTO: id, name, and built-in flag, plus the salary payload emitted in the same
     * export shape a month's income uses (children in {@code ordinal} order).
     */
    public SalaryPresetView toView(SalaryPreset preset) {
        return new SalaryPresetView(preset.getUuid(), preset.getName(), preset.isBuiltIn(),
                toSalaryView(preset));
    }

    /** Maps the preset's payroll fields to a salary view, each child collection in {@code ordinal} order. */
    private SalaryView toSalaryView(SalaryPreset preset) {
        return new SalaryView(
                preset.getName(), preset.getCurrency(), preset.getEngine(),
                ordered(preset.getComponents(), SalaryPresetComponent::getOrdinal).map(component ->
                        new ComponentView(component.getLabel(), component.getAmount(),
                                component.isTaxable(), component.isBasic(), component.getVarName(),
                                component.isVarAuto())).toList(),
                ordered(preset.getDeductions(), SalaryPresetDeduction::getOrdinal).map(deduction ->
                        new DeductionView(deduction.getLabel(), deduction.getType(),
                                deduction.getBase(), deduction.getBaseVar(), deduction.getRate(),
                                deduction.getCap(), deduction.getFloorAmount(), deduction.getAmount(),
                                deduction.getExpr(), deduction.getFn(), deduction.isPretax(),
                                deduction.getVarName(), deduction.isVarAuto(),
                                bracketViews(deduction.getBrackets()))).toList(),
                ordered(preset.getVariables(), SalaryPresetVariable::getOrdinal).map(variable ->
                        new VariableView(variable.getVarName(), variable.getLabel(),
                                variable.getType(), variable.getBase(), variable.getBaseVar(),
                                variable.getRate(), variable.getCap(), variable.getFloorAmount(),
                                variable.getAmount(), variable.getExpr(), variable.isVarAuto(),
                                bracketViews(variable.getBrackets()))).toList());
    }

    /** Maps a bracket list (a deduction's or variable's) to bracket views in {@code ordinal} order. */
    private List<BracketView> bracketViews(List<SalaryPresetBracket> brackets) {
        return ordered(brackets, SalaryPresetBracket::getOrdinal).map(bracket ->
                new BracketView(bracket.getVarName(), bracket.getOp(), bracket.getVal(),
                        bracket.getType(), bracket.getRate(), bracket.getExpr())).toList();
    }

    /**
     * Builds a new preset entity from a salary view, with the given name and built-in flag. Each
     * child's list position becomes its {@code ordinal}, and every child is wired back to the preset
     * so one cascade persist writes the whole graph; an unset engine defaults to the generic evaluator.
     */
    public SalaryPreset toEntity(String name,
                                 boolean builtIn,
                                 SalaryView salary) {
        final var preset = new SalaryPreset().setName(name).setBuiltIn(builtIn)
                .setCurrency(salary.currency())
                .setEngine(salary.engine() == null ? "generic" : salary.engine());

        forEachIndexed(salary.components(), (component, index) -> preset.getComponents().add(
                new SalaryPresetComponent().setSalaryPreset(preset).setOrdinal(index)
                        .setLabel(component.label()).setAmount(nz(component.amount()))
                        .setTaxable(component.taxable()).setBasic(component.basic())
                        .setVarName(component.var()).setVarAuto(component.varAuto())));
        forEachIndexed(salary.deductions(), (deduction, index) -> {
            final var entity = new SalaryPresetDeduction().setSalaryPreset(preset).setOrdinal(index)
                    .setLabel(deduction.label()).setType(deduction.type()).setBase(deduction.base())
                    .setBaseVar(deduction.baseVar()).setRate(deduction.rate()).setCap(deduction.cap())
                    .setFloorAmount(deduction.floorAmount()).setAmount(nz(deduction.amount()))
                    .setExpr(deduction.expr()).setFn(deduction.fn()).setPretax(deduction.pretax())
                    .setVarName(deduction.var()).setVarAuto(deduction.varAuto());
            forEachIndexed(deduction.brackets(), (bracket, bracketIndex) ->
                    entity.getBrackets().add(baseBracket(bracket, bracketIndex).setDeduction(entity)));
            preset.getDeductions().add(entity);
        });
        forEachIndexed(salary.variables(), (variable, index) -> {
            final var entity = new SalaryPresetVariable().setSalaryPreset(preset).setOrdinal(index)
                    .setVarName(variable.var()).setLabel(variable.label()).setType(variable.type())
                    .setBase(variable.base()).setBaseVar(variable.baseVar()).setRate(variable.rate())
                    .setCap(variable.cap()).setFloorAmount(variable.floorAmount())
                    .setAmount(nz(variable.amount())).setExpr(variable.expr()).setVarAuto(variable.varAuto());
            forEachIndexed(variable.brackets(), (bracket, bracketIndex) ->
                    entity.getBrackets().add(baseBracket(bracket, bracketIndex).setVariable(entity)));
            preset.getVariables().add(entity);
        });
        return preset;
    }

    /** The shared bracket fields; the caller sets whichever parent (deduction or variable) owns it. */
    private SalaryPresetBracket baseBracket(BracketView view,
                                            int ordinal) {
        return new SalaryPresetBracket().setOrdinal(ordinal).setVarName(view.var()).setOp(view.op())
                .setVal(view.val()).setType(view.type()).setRate(view.rate()).setExpr(view.expr());
    }

    /** Streams a child list in stored display order (ascending by its {@code ordinal} key). */
    private static <T> Stream<T> ordered(List<T> list,
                                         ToIntFunction<T> key) {
        return list.stream().sorted(Comparator.comparingInt(key));
    }

    /**
     * Applies {@code action} to each item with its list index (the index that becomes the child's
     * {@code ordinal}). A null list is a no-op.
     */
    private static <T> void forEachIndexed(List<T> list,
                                           ObjIntConsumer<T> action) {
        if (list == null) {
            return;
        }

        for (var index = 0; index < list.size(); index++) {
            action.accept(list.get(index), index);
        }
    }

    /** Null-to-zero: a blank amount in the view persists as {@code BigDecimal.ZERO}, never null. */
    private static BigDecimal nz(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
