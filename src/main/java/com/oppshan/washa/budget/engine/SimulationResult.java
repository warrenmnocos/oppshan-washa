package com.oppshan.washa.budget.engine;

import java.math.BigDecimal;

/**
 * Outcome of a debt payoff simulation. {@code months} is {@link Integer#MAX_VALUE} when the
 * payment never covers interest (a non-amortizing loan).
 */
public record SimulationResult(int months, BigDecimal totalInterest, BigDecimal finalPayment) {

    public static final int NEVER_AMORTIZES = Integer.MAX_VALUE;

    public boolean amortizes() {
        return months != NEVER_AMORTIZES;
    }
}
