package com.oppshan.washa.budget;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * API DTO for one month — the mockup's export {@code data} shape (HANDOVER §3), so the Angular UI
 * and the JSON export/import round-trip the same structure. JSON field names match the mockup
 * ({@code amt}, {@code cur}, {@code var}, {@code wd}, …) via {@link JsonProperty}.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record BudgetMonthView(
        List<SalaryView> salaries,
        List<ExpenseView> expenses,
        List<GoalView> goals,
        List<DebtView> debts,
        List<CurrencyView> cur) {

    public record SalaryView(
            String name,
            String currency,
            String engine,
            List<ComponentView> components,
            List<DeductionView> deductions,
            List<VariableView> variables) {
    }

    public record ComponentView(
            String label,
            BigDecimal amount,
            boolean taxable,
            boolean basic,
            @JsonProperty("var") String var,
            boolean varAuto) {
    }

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

    public record BracketView(
            @JsonProperty("var") String var,
            BracketOp op,
            BigDecimal val,
            BracketType type,
            BigDecimal rate,
            String expr) {
    }

    public record ExpenseView(
            String label,
            @JsonProperty("amt") BigDecimal amount,
            @JsonProperty("cur") String currency,
            String auto) {
    }

    public record GoalView(
            String label,
            @JsonProperty("amt") BigDecimal amount,
            @JsonProperty("cur") String currency,
            TargetView target,
            boolean savings,
            @JsonProperty("wd") BigDecimal withdrawal) {
    }

    public record TargetView(
            GoalTargetType type,
            BigDecimal amount,
            String base,
            BigDecimal mult) {
    }

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

    public record RateStepView(
            @JsonProperty("afterYears") BigDecimal afterYears,
            BigDecimal rate) {
    }

    public record CurrencyView(
            String code,
            @JsonProperty("sym") String symbol) {
    }
}
