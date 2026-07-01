package com.oppshan.washa.budget;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.quarkus.runtime.annotations.RegisterForReflection;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * API DTO for one month, shaped like the mockup's export {@code data}, the same
 * structure the JSON export/import round-trips. JSON field names match the mockup ({@code amt},
 * {@code cur}, {@code var}, {@code wd}, …) via {@link JsonProperty}.
 */
@RegisterForReflection
@JsonInclude(JsonInclude.Include.NON_NULL)
public record BudgetMonthView(
        List<SalaryView> salaries,
        List<ExpenseView> expenses,
        List<GoalView> goals,
        List<DebtView> debts,
        List<CurrencyView> cur,
        Map<String, BigDecimal> fxRates) {

    /**
     * The working (unsaved) exchange rates (quote code → units per one base unit) ride along only on a
     * live recompute, so a rate change evaluates against them without persisting. Persisted reads and
     * saves leave them out (the rates live in {@code fx_rate}); this 5-arg constructor builds the view
     * without them.
     */
    public BudgetMonthView(List<SalaryView> salaries,
                           List<ExpenseView> expenses,
                           List<GoalView> goals,
                           List<DebtView> debts,
                           List<CurrencyView> cur) {
        this(salaries, expenses, goals, debts, cur, null);
    }

    /**
     * One salary configuration: identity ({@code name}, {@code currency}), the
     * engine-template id ({@code engine}, defaulting to {@code "generic"}), and the ordered
     * {@code components} (gross-pay lines), {@code deductions}, and custom {@code variables}. List
     * position is the persisted ordinal, and deductions evaluate in that order.
     */
    @RegisterForReflection
    public record SalaryView(
            String name,
            String currency,
            String engine,
            List<ComponentView> components,
            List<DeductionView> deductions,
            List<VariableView> variables) {
    }

    /**
     * One gross-pay line. {@code taxable} counts it toward the taxable base; {@code basic} counts it
     * toward the "basic pay" figure some deductions use as their base. {@code var} publishes this
     * line's amount into the formula scope under that name, so a later variable, formula, or bracket
     * can read it; {@code varAuto} is true when that name is auto-managed rather than user-entered.
     */
    @RegisterForReflection
    public record ComponentView(
            String label,
            BigDecimal amount,
            boolean taxable,
            boolean basic,
            @JsonProperty("var") String var,
            boolean varAuto) {
    }

    /**
     * One salary deduction. {@code type} picks how it computes and which input it reads: {@code rate}
     * against {@code base}/{@code baseVar} (PCT), a flat {@code amount} (FIXED), an {@code expr}
     * (FORMULA), or the {@code brackets} table (BRACKETS). {@code base} is the percentage base and
     * {@code baseVar} names the scope variable when {@code base} is VAR; {@code cap} and
     * {@code floorAmount} clamp the result high/low. {@code pretax} lowers the taxable base for later
     * deductions (social-insurance style). {@code fn} is an optional named built-in reference (e.g. a
     * Japan income/resident tax preset) carried from the mockup; {@code type} drives the computation,
     * not {@code fn}. {@code var}/{@code varAuto} publish the computed amount into scope like a
     * component's. JSON: {@code floorAmount} serializes as {@code floor}, and {@code type} also
     * accepts the mockup's {@code kind} on input.
     */
    @RegisterForReflection
    public record DeductionView(
            String label,
            @JsonAlias("kind") DeductionType type,
            DeductionBase base,
            String baseVar,
            BigDecimal rate,
            BigDecimal cap,
            @JsonProperty("floor") BigDecimal floorAmount,
            BigDecimal amount,
            String expr,
            String fn,
            boolean pretax,
            @JsonProperty("var") String var,
            boolean varAuto,
            List<BracketView> brackets) {
    }

    /**
     * One salary custom variable: a named intermediate value computed the same way a deduction is
     * (see {@link DeductionView}; {@link VariableType} mirrors {@link DeductionType}) and published
     * into the formula scope under {@code var}, so later deductions, formulas, and brackets can read
     * it. {@code label} is the display name; {@code varAuto} is true when {@code var} is auto-managed.
     */
    @RegisterForReflection
    public record VariableView(
            @JsonProperty("var") String var,
            String label,
            @JsonAlias("kind") VariableType type,
            DeductionBase base,
            String baseVar,
            BigDecimal rate,
            BigDecimal cap,
            @JsonProperty("floor") BigDecimal floorAmount,
            BigDecimal amount,
            String expr,
            boolean varAuto,
            List<BracketView> brackets) {
    }

    /**
     * One row of a bracket table (a tiered/progressive rule; rows are additive). The row tests a
     * left-hand scope value (named by {@code var}, defaulting to {@code "taxable"}) against {@code val}
     * using {@code op}; when the test holds, it contributes per {@code type}, reading {@code rate} or
     * {@code expr}. So {@code val} is the threshold and {@code op} the comparison.
     */
    @RegisterForReflection
    public record BracketView(
            @JsonProperty("var") String var,
            BracketOp op,
            BigDecimal val,
            BracketType type,
            BigDecimal rate,
            String expr) {
    }

    /**
     * One expense line. {@code amt} is the amount and {@code cur} its currency. {@code auto} marks a
     * derived line: {@code "tithe"} is the auto tithe expense, which carries no entered {@code amt}
     * because its value is derived as 10% of net.
     */
    @RegisterForReflection
    public record ExpenseView(
            String label,
            @JsonProperty("amt") BigDecimal amount,
            @JsonProperty("cur") String currency,
            String auto) {
    }

    /**
     * One savings or spending goal. {@code amt} is this month's contribution and {@code cur} its
     * currency; {@code target} is the goal's target spec (see {@link TargetView}); {@code savings}
     * flags a savings-type goal (the ones summed into the running savings balance). {@code wd} is a
     * withdrawal taken this month. {@code closed} marks the goal closed, and {@code closedKey} is the
     * {@code "YYYY-MM"} month it was closed in, which attributes the closure to that month.
     */
    @RegisterForReflection
    public record GoalView(
            String label,
            @JsonProperty("amt") BigDecimal amount,
            @JsonProperty("cur") String currency,
            TargetView target,
            boolean savings,
            @JsonProperty("wd") BigDecimal withdrawal,
            boolean closed,
            @JsonProperty("closedKey") String closedKey) {
    }

    /**
     * A goal's target (see {@link GoalTargetType} for the kinds). AMOUNT uses the fixed {@code amount};
     * RELATIVE uses {@code mult} as a multiple of combined net, with {@code base} naming what it's
     * relative to (carried from the mockup, where it's always net); TIME uses either an explicit
     * {@code due} date or a period of {@code n} {@code unit}s (days/weeks/months/years) from the goal's
     * start. JSON: {@code due} is {@code dueDate}, {@code n} is {@code periodCount}.
     */
    @RegisterForReflection
    public record TargetView(
            GoalTargetType type,
            BigDecimal amount,
            String base,
            BigDecimal mult,
            @JsonProperty("due") java.time.LocalDate dueDate,
            @JsonProperty("n") Integer periodCount,
            String unit) {
    }

    /**
     * One debt/loan. {@code principal}, {@code annualRate} (annual interest), and {@code monthly} (the
     * scheduled payment) drive the amortization; {@code termMonths} is the optional term and
     * {@code repriceMode} how the payment or term reacts when the rate changes (see
     * {@link DebtRepriceMode}). {@code cur} is its currency. {@code prepay} flags an annual principal
     * prepayment, with {@code prepayAmt}/{@code prepayCur} its amount and currency (which may differ
     * from the debt's own). {@code rateSteps} are scheduled rate changes over the loan's life.
     */
    @RegisterForReflection
    public record DebtView(
            String name,
            BigDecimal principal,
            @JsonProperty("annualRate") BigDecimal annualRate,
            BigDecimal monthly,
            @JsonProperty("termMonths") Integer termMonths,
            @JsonProperty("repriceMode") DebtRepriceMode repriceMode,
            @JsonProperty("cur") String currency,
            boolean prepay,
            @JsonProperty("prepayAmt") BigDecimal prepayAmount,
            @JsonProperty("prepayCur") String prepayCurrency,
            List<RateStepView> rateSteps) {
    }

    /** One scheduled rate change: after {@code afterYears} years the debt's rate becomes {@code rate}. */
    @RegisterForReflection
    public record RateStepView(
            @JsonProperty("afterYears") BigDecimal afterYears,
            BigDecimal rate) {
    }

    /** One currency in the household list: its {@code code} and {@code sym} (the display symbol). */
    @RegisterForReflection
    public record CurrencyView(
            String code,
            @JsonProperty("sym") String symbol) {
    }
}
