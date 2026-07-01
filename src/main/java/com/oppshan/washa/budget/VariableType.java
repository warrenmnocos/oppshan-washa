package com.oppshan.washa.budget;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import io.quarkus.runtime.annotations.RegisterForReflection;

/**
 * How a salary custom variable is computed. The four kinds and the input each reads mirror
 * {@code DeductionType} exactly ({@code PCT}/{@code FIXED}/{@code FORMULA}/{@code BRACKETS}), and each
 * maps to the same-named {@code DeductionType}. Constants are UPPER_CASE per Java convention and
 * that's what the relational column stores; the lowercase {@link #getValue()} is only the JSON wire
 * token, mirroring the TypeScript {@code VariableType} 1:1.
 */
@RegisterForReflection
public enum VariableType {
    PCT("variableType.pct"),
    FIXED("variableType.fixed"),
    FORMULA("variableType.formula"),
    BRACKETS("variableType.brackets");

    private final String value;

    /** Binds the constant to its JSON wire token. */
    VariableType(String value) {
        this.value = value;
    }

    /** This constant's JSON wire token (the {@code @JsonValue} form). */
    @JsonValue
    public String getValue() {
        return value;
    }

    /** Resolves the constant whose wire token is {@code value}, or throws {@code IllegalArgumentException}. */
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
