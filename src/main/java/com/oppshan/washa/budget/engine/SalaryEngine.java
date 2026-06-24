package com.oppshan.washa.budget.engine;

import com.oppshan.washa.budget.DeductionBase;
import com.oppshan.washa.budget.Income;
import com.oppshan.washa.budget.IncomeComponent;
import com.oppshan.washa.budget.IncomeDeduction;
import com.oppshan.washa.budget.IncomeVariable;
import com.oppshan.washa.budget.SalaryBracket;
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
            final var value = computeRule(variable.getType().value(), baseName(variable.getBase()), variable.getBaseVar(),
                    variable.getRate(), variable.getAmount(), variable.getExpr(), variable.getBrackets(),
                    variable.getFloorAmount(), variable.getCap(), scope);
            if (variable.getVarName() != null) {
                scope.put(variable.getVarName().toLowerCase(), value);
            }
        }

        final var lines = new ArrayList<DeductionLine>();
        var totalDeductions = BigDecimal.ZERO;
        for (final var deduction : sorted(income.getDeductions(), IncomeDeduction::getOrdinal)) {
            final var amount = computeRule(deduction.getType().value(), baseName(deduction.getBase()), deduction.getBaseVar(),
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

    private BigDecimal computeRule(String kind, String base, String baseVar, BigDecimal rate,
                                   BigDecimal fixedAmount, String expr, List<SalaryBracket> brackets,
                                   BigDecimal floor, BigDecimal cap, Map<String, BigDecimal> scope) {
        var value = switch (kind == null ? "fixed" : kind) {
            case "pct" -> baseValue(base, baseVar, scope).multiply(nullToZero(rate)).divide(HUNDRED);
            case "formula" -> formulaEvaluator.evaluate(expr == null ? "0" : expr, scope).value();
            case "brackets" -> bracketSum(brackets, scope);
            default -> nullToZero(fixedAmount); // fixed | computed
        };
        if (floor != null) {
            value = value.max(floor);
        }
        if (cap != null) {
            value = value.min(cap);
        }

        return value;
    }

    private BigDecimal baseValue(String base, String baseVar, Map<String, BigDecimal> scope) {
        final var key = "var".equals(base)
                ? (baseVar == null ? "gross" : baseVar.toLowerCase())
                : (base == null ? "gross" : base);
        return scope.getOrDefault(key, BigDecimal.ZERO);
    }

    // §6: additive — sum the contribution of every row whose condition holds.
    private BigDecimal bracketSum(List<SalaryBracket> brackets, Map<String, BigDecimal> scope) {
        var sum = BigDecimal.ZERO;
        for (final var bracket : sorted(brackets, SalaryBracket::getOrdinal)) {
            final var lhsKey = bracket.getVarName() == null ? "taxable" : bracket.getVarName().toLowerCase();
            final var lhs = scope.getOrDefault(lhsKey, BigDecimal.ZERO);
            if (!conditionHolds(lhs, bracket.getOp(), nullToZero(bracket.getVal()))) {
                continue;
            }
            sum = sum.add(bracketContribution(bracket, scope));
        }

        return sum;
    }

    private BigDecimal bracketContribution(SalaryBracket bracket, Map<String, BigDecimal> scope) {
        return switch (bracket.getType() == null ? "fixed" : bracket.getType()) {
            case "formula" -> formulaEvaluator.evaluate(
                    bracket.getExpr() == null ? "0" : bracket.getExpr(), scope).value();
            case "pctgross" -> scope.getOrDefault("gross", BigDecimal.ZERO)
                    .multiply(nullToZero(bracket.getRate())).divide(HUNDRED);
            case "pctbasic" -> scope.getOrDefault("basic", BigDecimal.ZERO)
                    .multiply(nullToZero(bracket.getRate())).divide(HUNDRED);
            default -> nullToZero(bracket.getRate());
        };
    }

    private boolean conditionHolds(BigDecimal lhs, String op, BigDecimal rhs) {
        final var comparison = lhs.compareTo(rhs);
        return switch (op == null ? "gt" : op) {
            case "gt" -> comparison > 0;
            case "gte" -> comparison >= 0;
            case "lt" -> comparison < 0;
            case "lte" -> comparison <= 0;
            case "eq" -> comparison == 0;
            default -> false;
        };
    }

    private static <T> List<T> sorted(List<T> list, ToIntFunction<T> key) {
        final var copy = new ArrayList<>(list);
        copy.sort(Comparator.comparingInt(key));
        return copy;
    }

    private static BigDecimal nullToZero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private static String baseName(DeductionBase base) {
        return base == null ? null : base.value(); // the engine matches on the lowercase wire token
    }
}
