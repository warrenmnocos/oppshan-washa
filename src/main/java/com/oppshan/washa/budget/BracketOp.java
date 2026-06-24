package com.oppshan.washa.budget;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * The comparison a tax bracket row applies to its left-hand value (HANDOVER §6). The constant is
 * UPPER_CASE per Java convention and is what the relational column stores via @Enumerated(STRING);
 * the lowercase {@link #value()} is only the JSON wire string, matching the TypeScript {@code BracketOp}.
 */
public enum BracketOp {
    GT("gt"),
    GTE("gte"),
    LT("lt"),
    LTE("lte"),
    EQ("eq");

    private final String value;

    BracketOp(String value) {
        this.value = value;
    }

    @JsonValue
    public String value() {
        return value;
    }

    @JsonCreator
    public static BracketOp fromValue(String value) {
        for (final var op : values()) {
            if (op.value.equals(value)) {
                return op;
            }
        }

        throw new IllegalArgumentException("Unknown bracket op: " + value);
    }
}
