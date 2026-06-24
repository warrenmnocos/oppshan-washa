package com.oppshan.washa.budget;

import jakarta.enterprise.context.ApplicationScoped;

import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Maps a {@link BudgetMonth} entity graph to/from the export-shaped {@link BudgetMonthView}.
 * List position becomes {@code ordinal} on the way in; entities are ordered by {@code ordinal}
 * on the way out. Back-references are wired so a cascade persist saves the whole graph.
 */
@ApplicationScoped
public class BudgetMapper {

    // ---------- entity -> view ----------

    public BudgetMonthView toView(BudgetMonth month, List<CurrencySetting> currencies) {
        return new BudgetMonthView(
                ordered(month.getIncomes(), Income::getOrdinal).map(this::toSalaryView).toList(),
                ordered(month.getExpenses(), Expense::getOrdinal).map(this::toExpenseView).toList(),
                ordered(month.getGoals(), Goal::getOrdinal).map(this::toGoalView).toList(),
                ordered(month.getDebts(), Debt::getOrdinal).map(this::toDebtView).toList(),
                currencies.stream()
                        .sorted(Comparator.comparingInt(CurrencySetting::getOrdinal))
                        .map(currency -> new BudgetMonthView.CurrencyView(currency.getCode(), currency.getSymbol()))
                        .toList());
    }

    private BudgetMonthView.SalaryView toSalaryView(Income income) {
        return new BudgetMonthView.SalaryView(
                income.getName(), income.getCurrency(), income.getEngine(),
                ordered(income.getComponents(), IncomeComponent::getOrdinal).map(component ->
                        new BudgetMonthView.ComponentView(component.getLabel(), component.getAmount(),
                                component.isTaxable(), component.isBasic(), component.getVarName(),
                                component.isVarAuto())).toList(),
                ordered(income.getDeductions(), IncomeDeduction::getOrdinal).map(deduction ->
                        new BudgetMonthView.DeductionView(deduction.getLabel(), deduction.getType(),
                                deduction.getBase(), deduction.getBaseVar(), deduction.getRate(),
                                deduction.getCap(), deduction.getFloorAmount(), deduction.getAmount(),
                                deduction.getExpr(), deduction.getFn(), deduction.isPretax(),
                                deduction.getVarName(), deduction.isVarAuto(),
                                bracketViews(deduction.getBrackets()))).toList(),
                ordered(income.getVariables(), IncomeVariable::getOrdinal).map(variable ->
                        new BudgetMonthView.VariableView(variable.getVarName(), variable.getLabel(),
                                variable.getType(), variable.getBase(), variable.getBaseVar(),
                                variable.getRate(), variable.getCap(), variable.getFloorAmount(),
                                variable.getAmount(), variable.getExpr(), variable.isVarAuto(),
                                bracketViews(variable.getBrackets()))).toList());
    }

    private List<BudgetMonthView.BracketView> bracketViews(List<SalaryBracket> brackets) {
        return ordered(brackets, SalaryBracket::getOrdinal).map(bracket ->
                new BudgetMonthView.BracketView(bracket.getVarName(), bracket.getOp(), bracket.getVal(),
                        bracket.getType(), bracket.getRate(), bracket.getExpr())).toList();
    }

    private BudgetMonthView.ExpenseView toExpenseView(Expense expense) {
        return new BudgetMonthView.ExpenseView(expense.getLabel(), expense.getAmount(),
                expense.getCurrency(), expense.getAuto());
    }

    private BudgetMonthView.GoalView toGoalView(Goal goal) {
        return new BudgetMonthView.GoalView(goal.getLabel(), goal.getAmount(), goal.getCurrency(),
                new BudgetMonthView.TargetView(goal.getTargetType(), goal.getTargetAmount(),
                        goal.getTargetBase(), goal.getTargetMult(), goal.getTargetDueDate(),
                        goal.getTargetPeriodCount(), goal.getTargetPeriodUnit()),
                goal.isSavings(), goal.getWithdrawal(), goal.isClosed(), goal.getClosedKey());
    }

    private BudgetMonthView.DebtView toDebtView(Debt debt) {
        return new BudgetMonthView.DebtView(debt.getName(), debt.getPrincipal(), debt.getAnnualRate(),
                debt.getMonthly(), debt.getTermMonths(), debt.getRepriceMode(), debt.getCurrency(),
                debt.isPrepay(), debt.getPrepayAmount(), debt.getPrepayCurrency(),
                ordered(debt.getRateSteps(), DebtRateStep::getOrdinal).map(step ->
                        new BudgetMonthView.RateStepView(step.getAfterYears(), step.getRate())).toList());
    }

    // ---------- view -> entity ----------

    public BudgetMonth toEntity(YearMonth yearMonth, BudgetMonthView view) {
        final var baseCurrency = (view.cur() != null && !view.cur().isEmpty())
                ? view.cur().getFirst().code() : "JPY";
        final var month = new BudgetMonth().setYearMonth(yearMonth).setBaseCurrency(baseCurrency);

        forEachIndexed(view.salaries(), (salary, index) -> month.getIncomes().add(toIncome(month, salary, index)));
        forEachIndexed(view.expenses(), (expense, index) -> month.getExpenses().add(toExpense(month, expense, index)));
        forEachIndexed(view.goals(), (goal, index) -> month.getGoals().add(toGoal(month, goal, index)));
        forEachIndexed(view.debts(), (debt, index) -> month.getDebts().add(toDebt(month, debt, index)));
        return month;
    }

    private Income toIncome(BudgetMonth month, BudgetMonthView.SalaryView view, int ordinal) {
        final var income = new Income().setBudgetMonth(month).setOrdinal(ordinal)
                .setName(view.name()).setCurrency(view.currency())
                .setEngine(view.engine() == null ? "generic" : view.engine());
        forEachIndexed(view.components(), (component, index) -> income.getComponents().add(
                new IncomeComponent().setIncome(income).setOrdinal(index).setLabel(component.label())
                        .setAmount(nz(component.amount())).setTaxable(component.taxable())
                        .setBasic(component.basic()).setVarName(component.var()).setVarAuto(component.varAuto())));
        forEachIndexed(view.deductions(), (deduction, index) -> {
            final var entity = new IncomeDeduction().setIncome(income).setOrdinal(index)
                    .setLabel(deduction.label()).setType(deduction.type()).setBase(deduction.base())
                    .setBaseVar(deduction.baseVar()).setRate(deduction.rate()).setCap(deduction.cap())
                    .setFloorAmount(deduction.floorAmount()).setAmount(nz(deduction.amount()))
                    .setExpr(deduction.expr()).setFn(deduction.fn()).setPretax(deduction.pretax())
                    .setVarName(deduction.var()).setVarAuto(deduction.varAuto());
            forEachIndexed(deduction.brackets(), (bracket, bracketIndex) ->
                    entity.getBrackets().add(toBracketForDeduction(entity, bracket, bracketIndex)));
            income.getDeductions().add(entity);
        });
        forEachIndexed(view.variables(), (variable, index) -> {
            final var entity = new IncomeVariable().setIncome(income).setOrdinal(index)
                    .setVarName(variable.var()).setLabel(variable.label()).setType(variable.type())
                    .setBase(variable.base()).setBaseVar(variable.baseVar()).setRate(variable.rate())
                    .setCap(variable.cap()).setFloorAmount(variable.floorAmount())
                    .setAmount(nz(variable.amount())).setExpr(variable.expr()).setVarAuto(variable.varAuto());
            forEachIndexed(variable.brackets(), (bracket, bracketIndex) ->
                    entity.getBrackets().add(toBracketForVariable(entity, bracket, bracketIndex)));
            income.getVariables().add(entity);
        });
        return income;
    }

    private SalaryBracket toBracketForDeduction(IncomeDeduction parent, BudgetMonthView.BracketView view, int ordinal) {
        return baseBracket(view, ordinal).setDeduction(parent);
    }

    private SalaryBracket toBracketForVariable(IncomeVariable parent, BudgetMonthView.BracketView view, int ordinal) {
        return baseBracket(view, ordinal).setVariable(parent);
    }

    private SalaryBracket baseBracket(BudgetMonthView.BracketView view, int ordinal) {
        return new SalaryBracket().setOrdinal(ordinal).setVarName(view.var()).setOp(view.op())
                .setVal(view.val()).setType(view.type()).setRate(view.rate()).setExpr(view.expr());
    }

    private Expense toExpense(BudgetMonth month, BudgetMonthView.ExpenseView view, int ordinal) {
        return new Expense().setBudgetMonth(month).setOrdinal(ordinal).setLabel(view.label())
                .setAmount(nz(view.amount())).setCurrency(view.currency()).setAuto(view.auto());
    }

    private Goal toGoal(BudgetMonth month, BudgetMonthView.GoalView view, int ordinal) {
        final var target = view.target() == null
                ? new BudgetMonthView.TargetView(GoalTargetType.OPEN, null, null, null, null, null, null)
                : view.target();
        return new Goal().setBudgetMonth(month).setOrdinal(ordinal).setLabel(view.label())
                .setAmount(nz(view.amount())).setCurrency(view.currency())
                .setTargetType(target.type())
                .setTargetAmount(target.amount()).setTargetBase(target.base()).setTargetMult(target.mult())
                .setTargetDueDate(target.dueDate()).setTargetPeriodCount(target.periodCount())
                .setTargetPeriodUnit(target.unit())
                .setSavings(view.savings()).setWithdrawal(nz(view.withdrawal()))
                .setClosed(view.closed()).setClosedKey(view.closedKey());
    }

    private Debt toDebt(BudgetMonth month, BudgetMonthView.DebtView view, int ordinal) {
        final var debt = new Debt().setBudgetMonth(month).setOrdinal(ordinal).setName(view.name())
                .setPrincipal(nz(view.principal())).setAnnualRate(nz(view.annualRate()))
                .setMonthly(nz(view.monthly())).setTermMonths(view.termMonths()).setRepriceMode(view.repriceMode())
                .setCurrency(view.currency()).setPrepay(view.prepay()).setPrepayAmount(nz(view.prepayAmount()))
                .setPrepayCurrency(view.prepayCurrency());
        forEachIndexed(view.rateSteps(), (step, index) -> debt.getRateSteps().add(
                new DebtRateStep().setDebt(debt).setOrdinal(index)
                        .setAfterYears(nz(step.afterYears())).setRate(nz(step.rate()))));
        return debt;
    }

    private static <T> java.util.stream.Stream<T> ordered(List<T> list, java.util.function.ToIntFunction<T> key) {
        return list.stream().sorted(Comparator.comparingInt(key));
    }

    private static <T> void forEachIndexed(List<T> list, java.util.function.ObjIntConsumer<T> action) {
        if (list == null) {
            return;
        }
        for (var index = 0; index < list.size(); index++) {
            action.accept(list.get(index), index);
        }
    }

    private static java.math.BigDecimal nz(java.math.BigDecimal value) {
        return value == null ? java.math.BigDecimal.ZERO : value;
    }
}
