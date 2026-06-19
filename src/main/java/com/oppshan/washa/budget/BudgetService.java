package com.oppshan.washa.budget;

import com.oppshan.washa.budget.engine.Breakdown;
import com.oppshan.washa.budget.engine.CurrencyConverter;
import com.oppshan.washa.budget.engine.SalaryEngine;
import com.oppshan.washa.budget.engine.TitheCalculator;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.HashMap;

/**
 * Budget computations over the persisted month graph: combined household net (each salary's net,
 * reduced to base currency and summed), the live tithe, and derived cumulative goal progress.
 * Cumulative figures are computed by summing month rows — never stored (HANDOVER §2, §9, §13).
 */
@Transactional
@ApplicationScoped
public class BudgetService {

    private final SalaryEngine salaryEngine;
    private final FxRateRepository fxRateRepository;
    private final GoalRepository goalRepository;

    @Inject
    public BudgetService(SalaryEngine salaryEngine,
                         FxRateRepository fxRateRepository,
                         GoalRepository goalRepository) {
        this.salaryEngine = salaryEngine;
        this.fxRateRepository = fxRateRepository;
        this.goalRepository = goalRepository;
    }

    /** Combined household net for a month, in base currency (HANDOVER §4.7). */
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
