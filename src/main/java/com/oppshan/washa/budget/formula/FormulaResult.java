package com.oppshan.washa.budget.formula;

import java.math.BigDecimal;

/** Result of evaluating a formula: a value, plus an error message when evaluation failed. */
public record FormulaResult(BigDecimal value, String error) {

    public static FormulaResult ok(BigDecimal value) {
        return new FormulaResult(value, null);
    }

    public static FormulaResult error(String message) {
        return new FormulaResult(BigDecimal.ZERO, message);
    }
}
