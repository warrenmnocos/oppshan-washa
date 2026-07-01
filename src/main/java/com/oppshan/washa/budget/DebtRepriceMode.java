package com.oppshan.washa.budget;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import io.quarkus.runtime.annotations.RegisterForReflection;

/**
 * How a debt reprices when its rate changes: re-amortize the payment, or extend the term. Constants are
 * UPPER_CASE per Java convention and that's what the relational column stores via
 * {@code @Enumerated(STRING)}; the lowercase {@link #getValue()} is only the JSON wire token, mirroring
 * the TypeScript {@code DebtRepriceMode} 1:1.
 */
@RegisterForReflection
public enum DebtRepriceMode {
    PAYMENT("debtRepriceMode.payment"),
    TERM("debtRepriceMode.term");

    private final String value;

    /** Binds the constant to its JSON wire token. */
    DebtRepriceMode(String value) {
        this.value = value;
    }

    /** This constant's JSON wire token (the {@code @JsonValue} form). */
    @JsonValue
    public String getValue() {
        return value;
    }

    /** Resolves the constant whose wire token is {@code value}, or throws {@code IllegalArgumentException}. */
    @JsonCreator
    public static DebtRepriceMode fromValue(String value) {
        for (final var mode : values()) {
            if (mode.value.equals(value)) {
                return mode;
            }
        }

        throw new IllegalArgumentException("Unknown debt reprice mode: " + value);
    }
}
