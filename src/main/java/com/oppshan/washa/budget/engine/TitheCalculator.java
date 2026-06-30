package com.oppshan.washa.budget.engine;

import java.math.BigDecimal;

/** Tithe = exactly 10% of combined net take-home, in base currency (HANDOVER §9). */
public final class TitheCalculator {

    private static final BigDecimal TITHE_RATE = new BigDecimal("0.10");

    private TitheCalculator() {
    }

    public static BigDecimal tithe(BigDecimal combinedNetInBase) {
        return combinedNetInBase.multiply(TITHE_RATE);
    }
}
