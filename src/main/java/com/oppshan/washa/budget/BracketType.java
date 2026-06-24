package com.oppshan.washa.budget;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * How a tax bracket row contributes when its condition holds (HANDOVER §6): a fixed rate amount, a
 * formula, or a percentage of gross/basic. The constant is UPPER_CASE per Java convention and is what
 * the relational column stores via @Enumerated(STRING); the lowercase {@link #value()} is only the
 * JSON wire string, matching the TypeScript {@code BracketType}.
 */
public enum BracketType {
    FIXED("fixed"),
    FORMULA("formula"),
    PCTGROSS("pctgross"),
    PCTBASIC("pctbasic");

    private final String value;

    BracketType(String value) {
        this.value = value;
    }

    @JsonValue
    public String value() {
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
