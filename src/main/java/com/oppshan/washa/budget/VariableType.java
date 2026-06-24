package com.oppshan.washa.budget;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * How a salary custom variable is computed (the same rule kinds a deduction supports). The constant is
 * UPPER_CASE per Java convention and is what the relational column stores; the lowercase
 * {@link #value()} is only the JSON wire string, matching the TypeScript {@code VariableType} 1:1.
 */
public enum VariableType {
    PCT("pct"),
    FIXED("fixed"),
    FORMULA("formula"),
    BRACKETS("brackets");

    private final String value;

    VariableType(String value) {
        this.value = value;
    }

    @JsonValue
    public String value() {
        return value;
    }

    @JsonCreator
    public static VariableType fromValue(String value) {
        for (final var type : values()) {
            if (type.value.equals(value)) {
                return type;
            }
        }

        throw new IllegalArgumentException("Unknown variable type: " + value);
    }
}
