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
 * {@link DebtRepriceMode#TERM} (and a {@code null} mode, for back-compat) keeps the monthly fixed and
 * lets the term extend as interest climbs.
 */
@ApplicationScoped
public class DebtSimulator {

    /** 34-digit HALF_UP context: enough digits not to drift across the amortization loop. */
    private static final MathContext MATH_CONTEXT = new MathContext(34, RoundingMode.HALF_UP);
    /** Divisor that turns a percent rate into a fraction. */
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);
    /** Divisor that turns an annual rate into a monthly one, and years into months. */
    private static final BigDecimal TWELVE = BigDecimal.valueOf(12);
    /** 100-year loop guard, and the derived-term ceiling for a loan that never amortizes. */
    private static final int MONTH_CAP = 1200;

    /**
     * Runs the month-by-month amortization for one debt, up to {@link #MONTH_CAP} months.
     *
     * <p>Each month accrues {@code interest = balance * monthlyRate}, then pays the balance down by
     * {@code payment - interest}; every 12th month also subtracts {@code annualExtraPrepayment}. When the
     * balance crosses zero the loop stops and reports that month, trimming the last payment so the balance
     * lands exactly on zero. Under {@link DebtRepriceMode#PAYMENT} the payment is re-derived whenever a
     * rate step changes the rate (holding the term); otherwise the payment stays fixed.
     *
     * <p>The early "never amortizes" exit (payment can't cover the interest) is skipped when
     * {@code annualExtraPrepayment} is non-zero, since a prepayment can still clear a loan whose regular
     * payment alone wouldn't. Such a debt then either pays off through the prepayments or runs the full
     * cap and reports {@link SimulationResult#NEVER_AMORTIZES}.
     *
     * @param annualExtraPrepayment extra principal paid once every 12 months (zero for the baseline run)
     */
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
                final var finalPayment = payment.add(balance);
                return new SimulationResult(month, totalInterest, finalPayment);
            }
        }

        return new SimulationResult(SimulationResult.NEVER_AMORTIZES, totalInterest, BigDecimal.ZERO);
    }

    /**
     * The level monthly payment that clears {@code balance} over {@code remainingMonths} at
     * {@code monthlyRate}: the standard annuity formula {@code P = bal * r / (1 - (1 + r)^-n)}. Falls
     * back to an even {@code bal / n} split when the rate is zero, and floors {@code n} at one month so a
     * rate step landing on the very last payment can't divide by zero.
     */
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

    /**
     * The loan's term in months: the stored value when set, otherwise solved from principal, base rate,
     * and monthly payment (HANDOVER §12, the prototype's {@code loanTermMonths}). PAYMENT-mode
     * re-amortization spreads the balance over the term remaining at a rate change, so a term must always
     * resolve. Degenerate inputs fall back deliberately: no payment gives 0, a zero rate gives
     * {@code ceil(principal / payment)}, and a payment that can't even cover the first month's interest
     * gives {@link #MONTH_CAP}. Otherwise it inverts the annuity formula,
     * {@code n = -ln(1 - principal*r/payment) / ln(1 + r)}, rounded up.
     */
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

    /** Ceiling of {@code numerator / denominator} as an int (the zero-rate term is just principal / payment, rounded up). */
    private static int ceilDivide(BigDecimal numerator,
                                  BigDecimal denominator) {
        return numerator.divide(denominator, 0, RoundingMode.CEILING).intValueExact();
    }

    /**
     * The monthly rate in effect at loan {@code month}: the base annual rate, overridden by the last rate
     * step whose {@code afterYears * 12 < month} (steps are sorted ascending and applied in turn, so the
     * highest qualifying threshold wins), then divided by 100 and by 12. The strict {@code <} means a step
     * at {@code afterYears} takes effect from month {@code afterYears * 12 + 1} onward.
     */
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
