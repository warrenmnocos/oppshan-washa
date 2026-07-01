package com.oppshan.washa.budget;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import io.quarkus.runtime.annotations.RegisterForReflection;

/**
 * The base a percentage deduction or variable applies to (HANDOVER §6); {@code VAR} resolves through
 * the companion {@code baseVar}. Constants are UPPER_CASE per Java convention and that's what the
 * relational column stores; the lowercase {@link #getValue()} is only the JSON wire token, mirroring
 * the TypeScript {@code DeductionBase} 1:1.
 */
@RegisterForReflection
public enum DeductionBase {
    /** Total gross pay. */
    GROSS("deductionBase.gross"),
    /** The "basic" pay figure (falls back to gross when no component is flagged basic). */
    BASIC("deductionBase.basic"),
    /** Taxable gross minus the pretax deductions applied so far. */
    TAXABLE("deductionBase.taxable"),
    /** Gross × 12. */
    ANNUAL("deductionBase.annual"),
    /** A named scope variable, chosen by the companion {@code baseVar}. */
    VAR("deductionBase.var");

    private final String value;

    /** Binds the constant to its JSON wire token. */
    DeductionBase(String value) {
        this.value = value;
    }

    /** This constant's JSON wire token (the {@code @JsonValue} form). */
    @JsonValue
    public String getValue() {
        return value;
    }

    /** Resolves the constant whose wire token is {@code value}, or throws {@code IllegalArgumentException}. */
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
