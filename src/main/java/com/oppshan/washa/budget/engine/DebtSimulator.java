package com.oppshan.washa.budget.engine;

import com.oppshan.washa.budget.Debt;
import com.oppshan.washa.budget.DebtRateStep;
import jakarta.enterprise.context.ApplicationScoped;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.Comparator;

/**
 * Month-by-month debt payoff simulation (HANDOVER §12). The monthly rate at loan month {@code m}
 * is the base annual rate overridden by the latest {@code rateStep} whose {@code afterYears*12 < m},
 * divided to a monthly fraction. An optional annual extra prepayment is applied every 12 months.
 * Returns {@link SimulationResult#NEVER_AMORTIZES} months if the payment never covers interest.
 */
@ApplicationScoped
public class DebtSimulator {

    private static final MathContext MATH_CONTEXT = new MathContext(34, RoundingMode.HALF_UP);
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);
    private static final BigDecimal TWELVE = BigDecimal.valueOf(12);
    private static final int MONTH_CAP = 1200;

    public SimulationResult simulate(Debt debt, BigDecimal annualExtraPrepayment) {
        var balance = debt.getPrincipal();
        final var payment = debt.getMonthly();
        var totalInterest = BigDecimal.ZERO;

        for (var month = 1; month <= MONTH_CAP; month++) {
            final var monthlyRate = monthlyRate(debt, month);
            final var interest = balance.multiply(monthlyRate);
            if (payment.subtract(interest).signum() <= 0 && annualExtraPrepayment.signum() == 0) {
                return new SimulationResult(SimulationResult.NEVER_AMORTIZES, totalInterest, BigDecimal.ZERO);
            }

            totalInterest = totalInterest.add(interest);
            balance = balance.subtract(payment.subtract(interest));
            if (month % 12 == 0) {
                balance = balance.subtract(annualExtraPrepayment);
            }
            if (balance.signum() <= 0) {
                final var finalPayment = payment.add(balance); // trim the last payment to zero out
                return new SimulationResult(month, totalInterest, finalPayment);
            }
        }

        return new SimulationResult(SimulationResult.NEVER_AMORTIZES, totalInterest, BigDecimal.ZERO);
    }

    BigDecimal monthlyRate(Debt debt, int month) {
        var annualRate = debt.getAnnualRate();
        final var steps = debt.getRateSteps().stream()
                .sorted(Comparator.comparing(DebtRateStep::getAfterYears))
                .toList();
        for (final var step : steps) {
            if (step.getAfterYears().multiply(TWELVE).compareTo(BigDecimal.valueOf(month)) < 0) {
                annualRate = step.getRate();
            }
        }

        return annualRate.divide(HUNDRED, MATH_CONTEXT).divide(TWELVE, MATH_CONTEXT);
    }
}
