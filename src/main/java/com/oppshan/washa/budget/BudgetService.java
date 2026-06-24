package com.oppshan.washa.budget;

import com.oppshan.washa.budget.engine.Breakdown;
import com.oppshan.washa.budget.engine.CurrencyConverter;
import com.oppshan.washa.budget.engine.DebtSimulator;
import com.oppshan.washa.budget.engine.SalaryEngine;
import com.oppshan.washa.budget.engine.TitheCalculator;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.UUID;

/**
 * Budget computations and month CRUD over the persisted graph. Combined household net (each
 * salary's net, reduced to base currency and summed), the live tithe, derived cumulative goal
 * progress, and the load/save/compute orchestration used by {@code /api/budget}. Cumulative
 * figures are summed from month rows, never stored (HANDOVER §2, §9, §13).
 */
@Transactional
@ApplicationScoped
public class BudgetService {

    private static final YearMonth COMPUTE_PLACEHOLDER = YearMonth.of(2000, 1);
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private final SalaryEngine salaryEngine;
    private final DebtSimulator debtSimulator;
    private final FxRateRepository fxRateRepository;
    private final GoalRepository goalRepository;
    private final BudgetMonthRepository budgetMonthRepository;
    private final CurrencySettingRepository currencySettingRepository;
    private final BudgetMapper budgetMapper;

    @Inject
    public BudgetService(SalaryEngine salaryEngine,
                         DebtSimulator debtSimulator,
                         FxRateRepository fxRateRepository,
                         GoalRepository goalRepository,
                         BudgetMonthRepository budgetMonthRepository,
                         CurrencySettingRepository currencySettingRepository,
                         BudgetMapper budgetMapper) {
        this.salaryEngine = salaryEngine;
        this.debtSimulator = debtSimulator;
        this.fxRateRepository = fxRateRepository;
        this.goalRepository = goalRepository;
        this.budgetMonthRepository = budgetMonthRepository;
        this.currencySettingRepository = currencySettingRepository;
        this.budgetMapper = budgetMapper;
    }

    /** Loads a month as the export-shaped view, or an empty month (with currencies) if absent. */
    public BudgetMonthView getMonth(YearMonth yearMonth) {
        final var currencies = currencySettingRepository.findAll().toList();
        return budgetMonthRepository.findByYearMonth(yearMonth)
                .map(budgetMonthRepository::attachWithSession) // managed copy: lazy graph loads in-tx
                .map(month -> budgetMapper.toView(month, currencies))
                .orElseGet(() -> new BudgetMonthView(List.of(), List.of(), List.of(), List.of(),
                        budgetMapper.toView(new BudgetMonth().setBaseCurrency("JPY"), currencies).cur()));
    }

    /** Upserts a month from the view (replace-on-conflict), stamping who last modified it. */
    public void saveMonth(YearMonth yearMonth, BudgetMonthView view, UUID modifiedBy) {
        budgetMonthRepository.findByYearMonth(yearMonth)
                .map(budgetMonthRepository::attachWithSession)
                .ifPresent(budgetMonthRepository::deleteWithSession);
        budgetMonthRepository.flushWithSession();

        final var month = budgetMapper.toEntity(yearMonth, view).setLastModifiedBy(modifiedBy);
        budgetMonthRepository.insertWithSession(month);
    }

    /** Live figures for an unsaved month view (no persistence). */
    public ComputedView compute(BudgetMonthView view) {
        final var month = budgetMapper.toEntity(COMPUTE_PLACEHOLDER, view);
        final var converter = converterFor(month);

        final var salaryNet = new LinkedHashMap<String, BigDecimal>();
        var moneyIn = BigDecimal.ZERO;
        for (final var income : month.getIncomes()) {
            final Breakdown breakdown = salaryEngine.compute(income);
            final var netInBase = converter.toBase(breakdown.net(), income.getCurrency());
            salaryNet.put(income.getName(), netInBase);
            moneyIn = moneyIn.add(netInBase);
        }

        // Tithe is derived from net (10%); the auto tithe expense line carries no entered amount, so
        // its derived value is what it contributes to money-out — but only when that line is present
        // (matching the baseline, where money-out is the sum of the expense lines).
        final var tithe = TitheCalculator.tithe(moneyIn);
        var otherExpenses = BigDecimal.ZERO;
        var titheAllocated = BigDecimal.ZERO;
        for (final var expense : month.getExpenses()) {
            if ("tithe".equals(expense.getAuto())) {
                titheAllocated = tithe;
            } else {
                otherExpenses = otherExpenses.add(converter.toBase(nullToZero(expense.getAmount()), expense.getCurrency()));
            }
        }

        var savingsGoals = BigDecimal.ZERO;
        var nonSavingsGoals = BigDecimal.ZERO;
        for (final var goal : month.getGoals()) {
            final var contribution = converter.toBase(nullToZero(goal.getAmount()), goal.getCurrency());
            if (goal.isSavings()) {
                savingsGoals = savingsGoals.add(contribution);
            } else {
                nonSavingsGoals = nonSavingsGoals.add(contribution);
            }
        }

        var debtAmortization = BigDecimal.ZERO;
        var debtPrepayment = BigDecimal.ZERO;
        final var debtProjections = new ArrayList<ComputedView.DebtProjection>();
        for (final var debt : month.getDebts()) {
            debtAmortization = debtAmortization.add(converter.toBase(nullToZero(debt.getMonthly()), debt.getCurrency()));

            // Annual prepayment, in the debt's own currency (the simulation works in that currency).
            var annualPrepayInDebtCurrency = BigDecimal.ZERO;
            if (debt.isPrepay()) {
                final var prepayCurrency = debt.getPrepayCurrency() == null ? debt.getCurrency() : debt.getPrepayCurrency();
                final var amountInBase = converter.toBase(nullToZero(debt.getPrepayAmount()), prepayCurrency);
                debtPrepayment = debtPrepayment.add(amountInBase);
                annualPrepayInDebtCurrency = amountInBase.multiply(converter.rateOf(debt.getCurrency()));
            }

            final var baseline = debtSimulator.simulate(debt, BigDecimal.ZERO);
            final var withPrepay = annualPrepayInDebtCurrency.signum() > 0
                    ? debtSimulator.simulate(debt, annualPrepayInDebtCurrency)
                    : baseline;
            debtProjections.add(new ComputedView.DebtProjection(debt.getName(),
                    baseline.months(), baseline.totalInterest(),
                    withPrepay.months(), withPrepay.totalInterest()));
        }
        final var debt = debtAmortization.add(debtPrepayment);

        // Money out is everything allocated: expenses (incl. the tithe line), all goals, and debt
        // (amortization + prepayment) (HANDOVER §4).
        final var moneyOut = otherExpenses.add(titheAllocated).add(savingsGoals).add(nonSavingsGoals).add(debt);
        final var free = moneyIn.subtract(moneyOut);
        final var savingsRate = savingsRate(moneyIn, otherExpenses, titheAllocated, nonSavingsGoals, debtAmortization);

        return new ComputedView(moneyIn, moneyOut, free, tithe, otherExpenses, debt,
                savingsGoals, nonSavingsGoals, savingsRate, salaryNet, debtProjections);
    }

    // Share of net income saved or left free: (net − expenses − non-savings goals − debt
    // amortization) / net, as a percentage to one decimal (HANDOVER §4). Prepayment is excluded —
    // it pays down principal, which counts as saving, so it stays in the numerator via `free`.
    private static BigDecimal savingsRate(BigDecimal moneyIn,
                                          BigDecimal otherExpenses,
                                          BigDecimal tithe,
                                          BigDecimal nonSavingsGoals,
                                          BigDecimal debtAmortization) {
        if (moneyIn.signum() <= 0) {
            return BigDecimal.ZERO;
        }

        final var saved = moneyIn.subtract(otherExpenses).subtract(tithe).subtract(nonSavingsGoals).subtract(debtAmortization);
        return saved.multiply(HUNDRED).divide(moneyIn, 1, RoundingMode.HALF_UP);
    }

    private static BigDecimal nullToZero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    /** Combined household net for a (loaded) month, in base currency (HANDOVER §4.7). */
    public BigDecimal combinedNet(BudgetMonth month) {
        final var converter = converterFor(month);
        var total = BigDecimal.ZERO;
        for (final var income : month.getIncomes()) {
            final Breakdown breakdown = salaryEngine.compute(income);
            total = total.add(converter.toBase(breakdown.net(), income.getCurrency()));
        }

        return total;
    }

    /** Tithe for a month: 10% of combined net (HANDOVER §9). */
    public BigDecimal tithe(BudgetMonth month) {
        return TitheCalculator.tithe(combinedNet(month));
    }

    /** A goal's cumulative progress before a month, in the goal's currency (HANDOVER §13). */
    public BigDecimal cumulativeGoalProgressBefore(String label, String currency, YearMonth before) {
        return goalRepository.sumContributionsBefore(label, currency, before);
    }

    private CurrencyConverter converterFor(BudgetMonth month) {
        final var ratesByCode = new HashMap<String, BigDecimal>();
        for (final var fxRate : fxRateRepository.findByBaseCurrency(month.getBaseCurrency())) {
            ratesByCode.put(fxRate.getId().getQuoteCurrency(), fxRate.getRate());
        }

        return new CurrencyConverter(month.getBaseCurrency(), ratesByCode);
    }
}
