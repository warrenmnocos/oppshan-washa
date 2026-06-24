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
import com.oppshan.washa.budget.formula.FormulaEvaluator;
import jakarta.enterprise.context.ApplicationScoped;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.ToIntFunction;

/**
 * Turns one salary config into a gross, an ordered list of deduction amounts, and a net
 * (HANDOVER §4, §6). Deductions evaluate in array order; a {@code pretax} deduction lowers
 * {@code taxable} for every later item, so ordering matters. Brackets are additive (§6). Each
 * deduction line is rounded to an integer.
 */
@ApplicationScoped
public class SalaryEngine {

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);
    private static final BigDecimal TWELVE = BigDecimal.valueOf(12);

    private final FormulaEvaluator formulaEvaluator = new FormulaEvaluator();

    public Breakdown compute(Income income) {
        var gross = BigDecimal.ZERO;
        var taxableGross = BigDecimal.ZERO;
        var basic = BigDecimal.ZERO;
        var anyBasic = false;
        final var scope = new HashMap<String, BigDecimal>();

        for (final var component : sorted(income.getComponents(), IncomeComponent::getOrdinal)) {
            final var amount = nullToZero(component.getAmount());
            gross = gross.add(amount);
            if (component.isTaxable()) {
                taxableGross = taxableGross.add(amount);
            }
            if (component.isBasic()) {
                basic = basic.add(amount);
                anyBasic = true;
            }
            if (component.getVarName() != null && !component.getVarName().isBlank()) {
                scope.put(component.getVarName().toLowerCase(), amount);
            }
        }
        if (!anyBasic) {
            basic = gross; // a basic figure must always exist (§4.1)
        }

        final var annual = gross.multiply(TWELVE);
        scope.put("gross", gross);
        scope.put("basic", basic);
        scope.put("annual", annual);

        var socialInsurance = BigDecimal.ZERO; // running sum of pretax deductions
        scope.put("taxable", taxable(taxableGross, socialInsurance));

        for (final var variable : sorted(income.getVariables(), IncomeVariable::getOrdinal)) {
            final var value = computeRule(ruleKind(variable.getType()), variable.getBase(), variable.getBaseVar(),
                    variable.getRate(), variable.getAmount(), variable.getExpr(), variable.getBrackets(),
                    variable.getFloorAmount(), variable.getCap(), scope);
            if (variable.getVarName() != null) {
                scope.put(variable.getVarName().toLowerCase(), value);
            }
        }

        final var lines = new ArrayList<DeductionLine>();
        var totalDeductions = BigDecimal.ZERO;
        for (final var deduction : sorted(income.getDeductions(), IncomeDeduction::getOrdinal)) {
            final var amount = computeRule(deduction.getType(), deduction.getBase(), deduction.getBaseVar(),
                    deduction.getRate(), deduction.getAmount(), deduction.getExpr(), deduction.getBrackets(),
                    deduction.getFloorAmount(), deduction.getCap(), scope)
                    .setScale(0, RoundingMode.HALF_UP); // §4.5 round each line to an integer
            lines.add(new DeductionLine(deduction.getLabel(), amount));
            totalDeductions = totalDeductions.add(amount);
            if (deduction.isPretax()) {
                socialInsurance = socialInsurance.add(amount);
                scope.put("taxable", taxable(taxableGross, socialInsurance));
            }
        }

        final var net = gross.subtract(totalDeductions);
        return new Breakdown(gross, basic, lines, net);
    }

    private BigDecimal taxable(BigDecimal taxableGross, BigDecimal socialInsurance) {
        return taxableGross.subtract(socialInsurance).max(BigDecimal.ZERO);
    }

    // Dispatch on the enum, not its wire value (which is now a namespaced i18n token). Deductions and
    // variables share these rule kinds, so a variable's VariableType maps to the matching DeductionType.
    private BigDecimal computeRule(DeductionType kind, DeductionBase base, String baseVar, BigDecimal rate,
                                   BigDecimal fixedAmount, String expr, List<SalaryBracket> brackets,
                                   BigDecimal floor, BigDecimal cap, Map<String, BigDecimal> scope) {
        var value = switch (kind == null ? DeductionType.FIXED : kind) {
            case PCT -> baseValue(base, baseVar, scope).multiply(nullToZero(rate)).divide(HUNDRED);
            case FORMULA -> formulaEvaluator.evaluate(expr == null ? "0" : expr, scope).value();
            case BRACKETS -> bracketSum(brackets, scope);
            case FIXED -> nullToZero(fixedAmount);
        };
        if (floor != null) {
            value = value.max(floor);
        }
        if (cap != null) {
            value = value.min(cap);
        }

        return value;
    }

    private BigDecimal baseValue(DeductionBase base, String baseVar, Map<String, BigDecimal> scope) {
        final var key = switch (base == null ? DeductionBase.GROSS : base) {
            case GROSS -> "gross";
            case BASIC -> "basic";
            case TAXABLE -> "taxable";
            case ANNUAL -> "annual";
            case VAR -> baseVar == null ? "gross" : baseVar.toLowerCase();
        };
        return scope.getOrDefault(key, BigDecimal.ZERO);
    }

    // A variable shares the deduction rule kinds (VariableType mirrors DeductionType).
    private static DeductionType ruleKind(VariableType type) {
        return DeductionType.valueOf(type.name());
    }

    // §6: additive — sum the contribution of every row whose condition holds. Each op/type carries
    // its own behavior (BracketOp.holds / BracketType.contribution), so this dispatches polymorphically.
    private BigDecimal bracketSum(List<SalaryBracket> brackets, Map<String, BigDecimal> scope) {
        var sum = BigDecimal.ZERO;
        for (final var bracket : sorted(brackets, SalaryBracket::getOrdinal)) {
            final var lhsKey = bracket.getVarName() == null ? "taxable" : bracket.getVarName().toLowerCase();
            final var lhs = scope.getOrDefault(lhsKey, BigDecimal.ZERO);
            final var op = bracket.getOp() == null ? BracketOp.GT : bracket.getOp();
            if (!op.holds(lhs.compareTo(nullToZero(bracket.getVal())))) {
                continue;
            }

            final var type = bracket.getType() == null ? BracketType.FIXED : bracket.getType();
            sum = sum.add(type.contribution(bracket.getRate(), bracket.getExpr(), scope, formulaEvaluator));
        }

        return sum;
    }

    private static <T> List<T> sorted(List<T> list, ToIntFunction<T> key) {
        final var copy = new ArrayList<>(list);
        copy.sort(Comparator.comparingInt(key));
        return copy;
    }

    private static BigDecimal nullToZero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
