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

    /** 34 significant digits, HALF_UP: enough headroom that a division doesn't visibly drift. */
    private static final MathContext MATH_CONTEXT = new MathContext(34, RoundingMode.HALF_UP);

    /** The currency everything reduces to; its own rate is treated as 1. */
    private final String baseCode;
    /** This month's snapshotted rates, each in units of that currency per one base unit. */
    private final Map<String, BigDecimal> ratesByCode;

    /** Holds the base currency and this month's rate snapshot for the reductions. */
    public CurrencyConverter(String baseCode,
                             Map<String, BigDecimal> ratesByCode) {
        this.baseCode = baseCode;
        this.ratesByCode = ratesByCode;
    }

    /**
     * The rate to divide by to reach base for {@code code}: 1 for the base currency itself or a null
     * code, and 1 as a safe fallback for an unknown or zero rate (a zero would divide-by-zero in
     * {@link #toBase}, and an unknown currency shouldn't silently rescale amounts). Otherwise the
     * snapshotted rate, in units of {@code code} per one base unit.
     */
    public BigDecimal rateOf(String code) {
        if (code == null || code.equalsIgnoreCase(baseCode)) {
            return BigDecimal.ONE;
        }

        final var rate = ratesByCode.get(code);
        return (rate == null || rate.signum() == 0) ? BigDecimal.ONE : rate;
    }

    /** Reduces {@code amount} (denominated in {@code code}) to the base currency: {@code amount / rateOf(code)}. */
    public BigDecimal toBase(BigDecimal amount,
                             String code) {
        return amount.divide(rateOf(code), MATH_CONTEXT);
    }
}
