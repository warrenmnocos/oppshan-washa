package com.oppshan.washa.common;

import com.oppshan.washa.budget.BracketOp;
import com.oppshan.washa.budget.BracketType;
import com.oppshan.washa.budget.BudgetMonthView;
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
import com.oppshan.washa.budget.ComputedView;
import com.oppshan.washa.budget.ComputedView.Activity;
import com.oppshan.washa.budget.ComputedView.DebtProjection;
import com.oppshan.washa.budget.ComputedView.DeductionLineView;
import com.oppshan.washa.budget.ComputedView.GoalProgress;
import com.oppshan.washa.budget.ComputedView.PrepayYear;
import com.oppshan.washa.budget.ComputedView.SalaryBreakdown;
import com.oppshan.washa.budget.DebtRepriceMode;
import com.oppshan.washa.budget.DeductionBase;
import com.oppshan.washa.budget.DeductionType;
import com.oppshan.washa.budget.FxRateRequest;
import com.oppshan.washa.budget.GoalTargetType;
import com.oppshan.washa.budget.SalaryPresetRequest;
import com.oppshan.washa.budget.SalaryPresetView;
import com.oppshan.washa.budget.VariableType;
import com.oppshan.washa.config.Person;
import com.oppshan.washa.exception.BusinessExceptionMapper;
import com.oppshan.washa.exception.BusinessExceptionMapper.ErrorBody;
import com.oppshan.washa.user.UserAccountView;
import io.quarkus.runtime.annotations.RegisterForReflection;

/**
 * Single registry of the JSON models GraalVM native image must keep reflection metadata for.
 * Jackson reaches these only by reflection, so without registration the native Lambda fails at
 * runtime with "No serializer found for class ... configure reflection for the class" — even though
 * every JVM-mode test passes. Add a new request/response DTO (or a domain enum a DTO carries) here.
 *
 * Deliberately NOT here: JPA {@code @Entity} classes (the Quarkus Hibernate ORM extension already
 * registers those for reflection at build time) and the engine/formula records (Breakdown,
 * FormulaResult, Node, ...) which are internal compute types, never serialized.
 */
@RegisterForReflection(targets = {
        // user
        UserAccountView.class,
        // budget — request bodies
        FxRateRequest.class,
        SalaryPresetRequest.class,
        // budget — preset view
        SalaryPresetView.class,
        // budget — month view + nested records
        BudgetMonthView.class,
        SalaryView.class,
        ComponentView.class,
        DeductionView.class,
        VariableView.class,
        BracketView.class,
        ExpenseView.class,
        GoalView.class,
        TargetView.class,
        DebtView.class,
        RateStepView.class,
        CurrencyView.class,
        // budget — computed view + nested records
        ComputedView.class,
        SalaryBreakdown.class,
        DeductionLineView.class,
        DebtProjection.class,
        GoalProgress.class,
        Activity.class,
        PrepayYear.class,
        // budget — domain enums carried by the DTOs
        DeductionType.class,
        DeductionBase.class,
        GoalTargetType.class,
        VariableType.class,
        BracketType.class,
        DebtRepriceMode.class,
        BracketOp.class,
        // config
        Person.class,
        // exception
        ErrorBody.class,
})
public final class NativeReflectionConfig {
    private NativeReflectionConfig() {
    }
}
