package com.oppshan.washa.budget;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * A goal's target kind: open-ended, a fixed amount, a multiple of net income, or a deadline (a due
 * date or a period from the goal's start, against which time progress is measured). The constant is
 * UPPER_CASE per Java convention and is what the relational column stores via @Enumerated(STRING);
 * the lowercase {@link #getValue()} is only the JSON wire string, matching the TypeScript
 * {@code GoalTargetType} 1:1.
 */
public enum GoalTargetType {
    OPEN("goalTargetType.open"),
    AMOUNT("goalTargetType.amount"),
    RELATIVE("goalTargetType.relative"),
    TIME("goalTargetType.time");

    private final String value;

    GoalTargetType(String value) {
        this.value = value;
    }

    @JsonValue
    public String getValue() {
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
