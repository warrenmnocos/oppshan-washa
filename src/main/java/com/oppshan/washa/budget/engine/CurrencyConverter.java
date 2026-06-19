package com.oppshan.washa.budget.engine;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.Map;

/**
 * Reduces amounts to the base currency (HANDOVER §10): {@code amountInBase = amount / rateOf(code)},
 * where a rate is units of that currency per one base unit and the base rate is 1. Built per month
 * from that month's snapshotted rates.
 */
public class CurrencyConverter {

    private static final MathContext MATH_CONTEXT = new MathContext(34, RoundingMode.HALF_UP);

    private final String baseCode;
    private final Map<String, BigDecimal> ratesByCode;

    public CurrencyConverter(String baseCode, Map<String, BigDecimal> ratesByCode) {
        this.baseCode = baseCode;
        this.ratesByCode = ratesByCode;
    }

    public BigDecimal rateOf(String code) {
        if (code == null || code.equalsIgnoreCase(baseCode)) {
            return BigDecimal.ONE;
        }

        final var rate = ratesByCode.get(code);
        return (rate == null || rate.signum() == 0) ? BigDecimal.ONE : rate;
    }

    public BigDecimal toBase(BigDecimal amount, String code) {
        return amount.divide(rateOf(code), MATH_CONTEXT);
    }
}
