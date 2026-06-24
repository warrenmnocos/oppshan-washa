package com.oppshan.washa.budget;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * How a salary deduction is computed (HANDOVER §6). The constant is UPPER_CASE per Java convention and
 * is what the relational column stores ({@code @Enumerated(STRING)} → {@code name()}); the lowercase
 * {@link #value()} is only the JSON wire string, matching the TypeScript {@code DeductionType} 1:1.
 */
public enum DeductionType {
    PCT("deductionType.pct"),
    FIXED("deductionType.fixed"),
    FORMULA("deductionType.formula"),
    BRACKETS("deductionType.brackets");

    private final String value;

    DeductionType(String value) {
        this.value = value;
    }

    @JsonValue
    public String value() {
        return value;
    }

    @JsonCreator
    public static DeductionType fromValue(String value) {
        for (final var type : values()) {
            if (type.value.equals(value)) {
                return type;
            }
        }

        throw new IllegalArgumentException("Unknown deduction type: " + value);
    }
}
