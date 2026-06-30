package com.oppshan.washa.budget;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * The base a percentage deduction or variable applies to (HANDOVER §6); {@code VAR} resolves through
 * the companion {@code baseVar}. The constant is UPPER_CASE per Java convention and is what the
 * relational column stores; the lowercase {@link #getValue()} is only the JSON wire string, matching the
 * TypeScript {@code DeductionBase} 1:1.
 */
public enum DeductionBase {
    GROSS("deductionBase.gross"),
    BASIC("deductionBase.basic"),
    TAXABLE("deductionBase.taxable"),
    ANNUAL("deductionBase.annual"),
    VAR("deductionBase.var");

    private final String value;

    DeductionBase(String value) {
        this.value = value;
    }

    @JsonValue
    public String getValue() {
        return value;
    }

    @JsonCreator
    public static DeductionBase fromValue(String value) {
        for (final var base : values()) {
            if (base.value.equals(value)) {
                return base;
            }
        }

        throw new IllegalArgumentException("Unknown deduction base: " + value);
    }
}
