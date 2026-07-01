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

    /** Divisor that turns a percent rate into a fraction. */
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);
    /** Multiplier that turns a monthly gross into an annual figure. */
    private static final BigDecimal TWELVE = BigDecimal.valueOf(12);

    /** Evaluates FORMULA-typed deductions, variables, and brackets; stateless, so a plain field is fine. */
    private final FormulaEvaluator formulaEvaluator = new FormulaEvaluator();

    /**
     * Turns one salary config into a {@link Breakdown}. The pipeline, in order:
     *
     * <ol>
     *   <li>Sum the components (ordinal order) into {@code gross}, the taxable-flagged subset into
     *       {@code taxableGross}, and the basic-flagged subset into {@code basic} (falling back to gross
     *       when nothing is flagged basic, §4.1). A component that names a variable also publishes its amount
     *       under that name.</li>
     *   <li>Seed the formula scope with the built-ins {@code gross}, {@code basic}, {@code annual}
     *       (gross × 12), and {@code taxable} (taxable gross minus the running pretax-deduction total, the
     *       JP social-insurance premiums, as they accrue). Any variable, deduction, or bracket formula can
     *       reference these built-ins by name.</li>
     *   <li>Evaluate the custom variables in ordinal order, each publishing its result into scope for the
     *       rows that follow. They see {@code taxable} before any pretax deduction has been applied.</li>
     *   <li>Evaluate the deductions in ordinal order, rounding each line to a whole unit (§4.5); a pretax
     *       deduction lowers {@code taxable} for every later line, so ordering is significant.</li>
     * </ol>
     *
     * Net is {@code gross} minus the summed (rounded) deduction lines. Every figure stays in the salary's
     * own currency.
     */
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
            basic = gross;
        }

        final var annual = gross.multiply(TWELVE);
        scope.put("gross", gross);
        scope.put("basic", basic);
        scope.put("annual", annual);

        var socialInsurance = BigDecimal.ZERO;
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
                    .setScale(0, RoundingMode.HALF_UP);
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

    /** The taxable base: taxable gross minus the pretax deductions so far, floored at zero (it can't go negative). */
    private BigDecimal taxable(BigDecimal taxableGross,
                               BigDecimal socialInsurance) {
        return taxableGross.subtract(socialInsurance).max(BigDecimal.ZERO);
    }

    /**
     * Computes one deduction or variable value by rule kind (dispatched on the enum, not its wire value,
     * which is now a namespaced i18n token). {@code PCT} is {@code rate%} of a base, {@code FORMULA}
     * evaluates an expression against the scope, {@code BRACKETS} sums the qualifying bracket rows, and
     * {@code FIXED} (also the fallback for a null kind) is a flat amount. An optional {@code floor}
     * (a minimum) and {@code cap} (a maximum) then clamp the result. Deductions and variables share
     * these kinds, so a variable's {@code VariableType} maps to the matching {@code DeductionType}.
     */
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

    /**
     * Resolves a percentage base to its scope value: the built-in for GROSS/BASIC/TAXABLE/ANNUAL, or the
     * companion {@code baseVar} for VAR (defaulting to gross when unset). A null base means GROSS, and a
     * missing key reads as zero.
     */
    private BigDecimal baseValue(DeductionBase base,
                                 String baseVar,
                                 Map<String, BigDecimal> scope) {
        final var key = switch (base == null ? DeductionBase.GROSS : base) {
            case GROSS -> "gross";
            case BASIC -> "basic";
            case TAXABLE -> "taxable";
            case ANNUAL -> "annual";
            case VAR -> baseVar == null ? "gross" : baseVar.toLowerCase();
        };
        return scope.getOrDefault(key, BigDecimal.ZERO);
    }

    /**
     * Maps a variable's {@code VariableType} to the matching {@code DeductionType} so variables can reuse
     * the deduction rule kinds. The two enums declare identical constant names, so {@code valueOf(name())}
     * maps one onto the other.
     */
    private static DeductionType ruleKind(VariableType type) {
        return DeductionType.valueOf(type.name());
    }

    /**
     * Additive bracket evaluation (§6): sum the contribution of every row whose condition holds. A row
     * compares a left-hand value (a named variable, or {@code taxable} by default) against its threshold
     * with its {@code BracketOp} (defaulting to GT), and when that holds adds its {@code BracketType}
     * contribution (defaulting to FIXED). Both the comparison and the contribution are strategies on the
     * enums, so this stays a straight loop rather than a switch.
     */
    private BigDecimal bracketSum(List<SalaryBracket> brackets,
                                  Map<String, BigDecimal> scope) {
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

    /** Returns a copy sorted by an int key (the ordinal), leaving the entity's live collection untouched. */
    private static <T> List<T> sorted(List<T> list,
                                      ToIntFunction<T> key) {
        final var copy = new ArrayList<>(list);
        copy.sort(Comparator.comparingInt(key));
        return copy;
    }

    /** Treats a null amount as zero, so an unset component, rate, or fixed amount contributes nothing. */
    private static BigDecimal nullToZero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
