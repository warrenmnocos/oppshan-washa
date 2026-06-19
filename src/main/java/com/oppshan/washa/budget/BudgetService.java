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
import java.time.YearMonth;
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

        final var tithe = TitheCalculator.tithe(moneyIn);
        var moneyOut = BigDecimal.ZERO;
        for (final var expense : month.getExpenses()) {
            final var amount = "tithe".equals(expense.getAuto()) ? tithe : expense.getAmount();
            moneyOut = moneyOut.add(converter.toBase(amount, expense.getCurrency()));
        }

        final var debtProjections = month.getDebts().stream().map(debt -> {
            final var result = debtSimulator.simulate(debt, BigDecimal.ZERO);
            return new ComputedView.DebtProjection(debt.getName(), result.months(), result.totalInterest());
        }).toList();

        return new ComputedView(moneyIn, moneyOut, moneyIn.subtract(moneyOut), tithe, salaryNet, debtProjections);
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
        fxRateRepository.findAll()
                .filter(fxRate -> month.getBaseCurrency().equals(fxRate.getId().getBaseCurrency()))
                .forEach(fxRate -> ratesByCode.put(fxRate.getId().getQuoteCurrency(), fxRate.getRate()));

        return new CurrencyConverter(month.getBaseCurrency(), ratesByCode);
    }
}
