package com.oppshan.washa.budget;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import com.oppshan.washa.budget.formula.FormulaEvaluator;
import io.quarkus.runtime.annotations.RegisterForReflection;

import java.math.BigDecimal;
import java.util.Map;

/**
 * How a tax bracket row contributes once its condition holds. Each constant carries its
 * own {@link #contribution} strategy, so the set has no central switch to keep in step. Constants are
 * UPPER_CASE per Java convention and that's what the relational column stores via
 * {@code @Enumerated(STRING)}; the lowercase {@link #getValue()} is the JSON wire token, mirroring the
 * TypeScript {@code BracketType}.
 */
@RegisterForReflection
public enum BracketType {
    /** Flat amount: the row's {@code rate} is used directly, not as a percentage. */
    FIXED("bracketType.fixed") {
        @Override
        public BigDecimal contribution(BigDecimal rate,
                                       String expr,
                                       Map<String, BigDecimal> scope,
                                       FormulaEvaluator evaluator) {
            return rate == null ? BigDecimal.ZERO : rate;
        }
    },
    /** Evaluates the row's {@code expr} against the salary scope. */
    FORMULA("bracketType.formula") {
        @Override
        public BigDecimal contribution(BigDecimal rate,
                                       String expr,
                                       Map<String, BigDecimal> scope,
                                       FormulaEvaluator evaluator) {
            return evaluator.evaluate(expr == null ? "0" : expr, scope).value();
        }
    },
    /** {@code rate}% of gross. */
    PCTGROSS("bracketType.pctgross") {
        @Override
        public BigDecimal contribution(BigDecimal rate,
                                       String expr,
                                       Map<String, BigDecimal> scope,
                                       FormulaEvaluator evaluator) {
            return percentageOf(scope.getOrDefault("gross", BigDecimal.ZERO), rate);
        }
    },
    /** {@code rate}% of basic. */
    PCTBASIC("bracketType.pctbasic") {
        @Override
        public BigDecimal contribution(BigDecimal rate,
                                       String expr,
                                       Map<String, BigDecimal> scope,
                                       FormulaEvaluator evaluator) {
            return percentageOf(scope.getOrDefault("basic", BigDecimal.ZERO), rate);
        }
    };

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private final String value;

    /** Binds the constant to its JSON wire token. */
    BracketType(String value) {
        this.value = value;
    }

    /** This row's contribution given its rate/expression and the salary scope. */
    public abstract BigDecimal contribution(BigDecimal rate,
                                            String expr,
                                            Map<String, BigDecimal> scope,
                                            FormulaEvaluator evaluator);

    /** Returns {@code rate}% of {@code amount}; a null {@code rate} counts as zero. */
    private static BigDecimal percentageOf(BigDecimal amount,
                                           BigDecimal rate) {
        return amount.multiply(rate == null ? BigDecimal.ZERO : rate).divide(HUNDRED);
    }

    /** This constant's JSON wire token (the {@code @JsonValue} form). */
    @JsonValue
    public String getValue() {
        return value;
    }

    /** Resolves the constant whose wire token is {@code value}, or throws {@code IllegalArgumentException}. */
    @JsonCreator
    public static BracketType fromValue(String value) {
        for (final var type : values()) {
            if (type.value.equals(value)) {
                return type;
            }
        }

        throw new IllegalArgumentException("Unknown bracket type: " + value);
    }
}
