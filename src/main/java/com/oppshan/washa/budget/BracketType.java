package com.oppshan.washa.budget;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import com.oppshan.washa.budget.formula.FormulaEvaluator;
import io.quarkus.runtime.annotations.RegisterForReflection;

import java.math.BigDecimal;
import java.util.Map;

/**
 * How a tax bracket row contributes when its condition holds (HANDOVER §6). Each constant carries its
 * own {@link #contribution} strategy, so the engine dispatches polymorphically rather than switching.
 * The constant is UPPER_CASE per Java convention and is what the relational column stores via
 * @Enumerated(STRING); the lowercase {@link #getValue()} is the JSON wire string, matching the
 * TypeScript {@code BracketType}.
 */
@RegisterForReflection
public enum BracketType {
    FIXED("bracketType.fixed") {
        @Override
        public BigDecimal contribution(BigDecimal rate,
                                       String expr,
                                       Map<String, BigDecimal> scope,
                                       FormulaEvaluator evaluator) {
            return rate == null ? BigDecimal.ZERO : rate;
        }
    },
    FORMULA("bracketType.formula") {
        @Override
        public BigDecimal contribution(BigDecimal rate,
                                       String expr,
                                       Map<String, BigDecimal> scope,
                                       FormulaEvaluator evaluator) {
            return evaluator.evaluate(expr == null ? "0" : expr, scope).value();
        }
    },
    PCTGROSS("bracketType.pctgross") {
        @Override
        public BigDecimal contribution(BigDecimal rate,
                                       String expr,
                                       Map<String, BigDecimal> scope,
                                       FormulaEvaluator evaluator) {
            return percentageOf(scope.getOrDefault("gross", BigDecimal.ZERO), rate);
        }
    },
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

    BracketType(String value) {
        this.value = value;
    }

    /** This row's contribution given its rate/expression and the salary scope. */
    public abstract BigDecimal contribution(BigDecimal rate,
                                            String expr,
                                            Map<String, BigDecimal> scope,
                                            FormulaEvaluator evaluator);

    private static BigDecimal percentageOf(BigDecimal amount,
                                           BigDecimal rate) {
        return amount.multiply(rate == null ? BigDecimal.ZERO : rate).divide(HUNDRED);
    }

    @JsonValue
    public String getValue() {
        return value;
    }

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
