package com.oppshan.washa.budget.engine;

import com.oppshan.washa.budget.Debt;
import com.oppshan.washa.budget.DebtRateStep;
import com.oppshan.washa.budget.DebtRepriceMode;
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
 *
 * <p>When a rate step changes the rate mid-loan, the {@link DebtRepriceMode} decides what gives:
 * {@link DebtRepriceMode#PAYMENT} re-amortizes the remaining balance over the remaining term at the
 * new rate (the monthly rises, the payoff term stays close to the original), while
 * {@link DebtRepriceMode#TERM} — and a {@code null} mode, for back-compat — keeps the monthly fixed
 * and lets the term extend as interest climbs.
 */
@ApplicationScoped
public class DebtSimulator {

    private static final MathContext MATH_CONTEXT = new MathContext(34, RoundingMode.HALF_UP);
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);
    private static final BigDecimal TWELVE = BigDecimal.valueOf(12);
    private static final int MONTH_CAP = 1200;

    public SimulationResult simulate(Debt debt,
                                     BigDecimal annualExtraPrepayment) {
        var balance = debt.getPrincipal();
        var payment = debt.getMonthly();
        var totalInterest = BigDecimal.ZERO;
        final var reAmortizes = debt.getRepriceMode() == DebtRepriceMode.PAYMENT;
        final var termMonths = termMonths(debt);

        for (var month = 1; month <= MONTH_CAP; month++) {
            final var monthlyRate = monthlyRate(debt, month);
            if (reAmortizes && month > 1 && monthlyRate.compareTo(monthlyRate(debt, month - 1)) != 0) {
                payment = amortizingPayment(balance, monthlyRate, termMonths - (month - 1));
            }

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

    // Standard amortization: the level payment that clears {@code balance} over {@code remainingMonths}
    // at monthly rate {@code monthlyRate}. P = bal * r / (1 - (1+r)^-n), with the r == 0 fallback
    // P = bal / n and a floor of one month so a step at the very last payment can't divide by zero.
    BigDecimal amortizingPayment(BigDecimal balance,
                                 BigDecimal monthlyRate,
                                 int remainingMonths) {
        final var months = Math.max(1, remainingMonths);
        if (monthlyRate.signum() <= 0) {
            return balance.divide(BigDecimal.valueOf(months), MATH_CONTEXT);
        }

        final var onePlusRate = BigDecimal.ONE.add(monthlyRate);
        final var discount = BigDecimal.ONE.subtract(BigDecimal.ONE.divide(onePlusRate.pow(months), MATH_CONTEXT));
        return balance.multiply(monthlyRate).divide(discount, MATH_CONTEXT);
    }

    // The loan term in months: the stored value when set, otherwise derived from the principal, base
    // rate, and monthly payment (HANDOVER §12, matching the prototype's loanTermMonths). Re-amortization
    // spreads the balance over the term remaining at the rate change, so the term must always resolve.
    int termMonths(Debt debt) {
        final var stored = debt.getTermMonths();
        if (stored != null && stored > 0) {
            return stored;
        }

        final var payment = debt.getMonthly();
        if (payment.signum() <= 0) {
            return 0;
        }

        final var monthlyRate = debt.getAnnualRate().divide(HUNDRED, MATH_CONTEXT).divide(TWELVE, MATH_CONTEXT);
        if (monthlyRate.signum() <= 0) {
            return Math.max(1, ceilDivide(debt.getPrincipal(), payment));
        }

        final var ratio = BigDecimal.ONE.subtract(debt.getPrincipal().multiply(monthlyRate).divide(payment, MATH_CONTEXT));
        if (ratio.signum() <= 0) {
            return MONTH_CAP;
        }

        final var months = -Math.log(ratio.doubleValue()) / Math.log(1 + monthlyRate.doubleValue());
        return Math.max(1, (int) Math.ceil(months));
    }

    private static int ceilDivide(BigDecimal numerator,
                                  BigDecimal denominator) {
        return numerator.divide(denominator, 0, RoundingMode.CEILING).intValueExact();
    }

    BigDecimal monthlyRate(Debt debt,
                           int month) {
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
