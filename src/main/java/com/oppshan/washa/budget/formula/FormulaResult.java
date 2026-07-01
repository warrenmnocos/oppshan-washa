package com.oppshan.washa.budget.formula;

import java.math.BigDecimal;

/**
 * Result of evaluating a formula. A success carries the computed {@code value} with a null
 * {@code error}; a failure carries the {@code error} message with {@code value} defaulted to zero, so a
 * caller that reads {@code value()} without checking {@code error()} still gets a safe fallback.
 */
public record FormulaResult(BigDecimal value, String error) {

    /** A success carrying {@code value} and a null error. */
    public static FormulaResult ok(BigDecimal value) {
        return new FormulaResult(value, null);
    }

    /** A failure carrying {@code message}, with {@code value} defaulted to zero for safe fallback reads. */
    public static FormulaResult error(String message) {
        return new FormulaResult(BigDecimal.ZERO, message);
    }
}
