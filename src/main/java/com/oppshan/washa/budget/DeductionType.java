package com.oppshan.washa.budget;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import io.quarkus.runtime.annotations.RegisterForReflection;

/**
 * How a salary deduction is computed (HANDOVER §6), which also picks the input it reads: {@code PCT}
 * takes a percentage {@code rate} of a base, {@code FIXED} uses a flat {@code amount}, {@code FORMULA}
 * evaluates an {@code expr}, and {@code BRACKETS} sums a bracket table. Constants are UPPER_CASE per
 * Java convention and that's what the relational column stores ({@code @Enumerated(STRING)} →
 * {@code name()}); the lowercase {@link #getValue()} is only the JSON wire token, mirroring the
 * TypeScript {@code DeductionType} 1:1.
 */
@RegisterForReflection
public enum DeductionType {
    PCT("deductionType.pct"),
    FIXED("deductionType.fixed"),
    FORMULA("deductionType.formula"),
    BRACKETS("deductionType.brackets");

    private final String value;

    /** Binds the constant to its JSON wire token. */
    DeductionType(String value) {
        this.value = value;
    }

    /** This constant's JSON wire token (the {@code @JsonValue} form). */
    @JsonValue
    public String getValue() {
        return value;
    }

    /** Resolves the constant whose wire token is {@code value}, or throws {@code IllegalArgumentException}. */
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
