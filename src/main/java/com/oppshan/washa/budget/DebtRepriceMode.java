package com.oppshan.washa.budget;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import io.quarkus.runtime.annotations.RegisterForReflection;

/**
 * How a debt reprices when its rate changes: re-amortize the payment, or extend the term. The constant
 * is UPPER_CASE per Java convention and is what the relational column stores via @Enumerated(STRING);
 * the lowercase {@link #getValue()} is only the JSON wire string, matching the TypeScript
 * {@code DebtRepriceMode} 1:1.
 */
@RegisterForReflection
public enum DebtRepriceMode {
    PAYMENT("debtRepriceMode.payment"),
    TERM("debtRepriceMode.term");

    private final String value;

    DebtRepriceMode(String value) {
        this.value = value;
    }

    @JsonValue
    public String getValue() {
        return value;
    }

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
