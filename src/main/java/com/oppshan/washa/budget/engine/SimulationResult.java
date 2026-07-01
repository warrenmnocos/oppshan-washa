package com.oppshan.washa.budget.engine;

import java.math.BigDecimal;

/**
 * Outcome of a debt payoff simulation: {@code months} is how many monthly payments clear the balance,
 * or {@link #NEVER_AMORTIZES} when the payment never covers the interest; {@code totalInterest} is the
 * interest accrued over the run; and {@code finalPayment} is the last payment, trimmed so the balance
 * lands exactly on zero (it's zero when the loan never amortizes).
 */
public record SimulationResult(int months, BigDecimal totalInterest, BigDecimal finalPayment) {

    /** Sentinel {@code months} value: the payment never outpaces the interest, so the balance never clears. */
    public static final int NEVER_AMORTIZES = Integer.MAX_VALUE;

    /** Whether the loan paid off within the cap (i.e. {@code months} isn't the sentinel). */
    public boolean amortizes() {
        return months != NEVER_AMORTIZES;
    }
}
