package com.oppshan.washa.budget.engine;

import java.math.BigDecimal;

/** Tithe = exactly 10% of combined net take-home, in base currency (HANDOVER §9). */
public final class TitheCalculator {

    /** Exact 0.10 built from a string: a {@code double} 0.1 has no exact binary representation and would drift. */
    private static final BigDecimal TITHE_RATE = new BigDecimal("0.10");

    /** Static utility: not instantiable. */
    private TitheCalculator() {
    }

    /** Ten percent of the combined net take-home passed in (already reduced to base currency). */
    public static BigDecimal tithe(BigDecimal combinedNetInBase) {
        return combinedNetInBase.multiply(TITHE_RATE);
    }
}
