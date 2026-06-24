package com.oppshan.washa.budget;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * A goal's target kind: open-ended, a fixed amount, or a multiple of net income. The constant is
 * UPPER_CASE per Java convention and is what the relational column stores via @Enumerated(STRING);
 * the lowercase {@link #value()} is only the JSON wire string, matching the TypeScript
 * {@code GoalTargetType} 1:1.
 */
public enum GoalTargetType {
    OPEN("goalTargetType.open"),
    AMOUNT("goalTargetType.amount"),
    RELATIVE("goalTargetType.relative");

    private final String value;

    GoalTargetType(String value) {
        this.value = value;
    }

    @JsonValue
    public String value() {
        return value;
    }

    @JsonCreator
    public static GoalTargetType fromValue(String value) {
        for (final var type : values()) {
            if (type.value.equals(value)) {
                return type;
            }
        }

        throw new IllegalArgumentException("Unknown goal target type: " + value);
    }
}
