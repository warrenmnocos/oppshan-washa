package com.oppshan.washa.budget;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * The base a percentage deduction or variable applies to (HANDOVER §6); {@code VAR} resolves through
 * the companion {@code baseVar}. The constant is UPPER_CASE per Java convention and is what the
 * relational column stores; the lowercase {@link #value()} is only the JSON wire string, matching the
 * TypeScript {@code DeductionBase} 1:1.
 */
public enum DeductionBase {
    GROSS("gross"),
    BASIC("basic"),
    TAXABLE("taxable"),
    ANNUAL("annual"),
    VAR("var");

    private final String value;

    DeductionBase(String value) {
        this.value = value;
    }

    @JsonValue
    public String value() {
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
