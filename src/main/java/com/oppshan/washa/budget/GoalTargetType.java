package com.oppshan.washa.budget;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import io.quarkus.runtime.annotations.RegisterForReflection;

/**
 * A goal's target kind: open-ended, a fixed amount, a multiple of net income, or a deadline (a due
 * date or a period from the goal's start, against which time progress is measured). Constants are
 * UPPER_CASE per Java convention and that's what the relational column stores via
 * {@code @Enumerated(STRING)}; the lowercase {@link #getValue()} is only the JSON wire token,
 * mirroring the TypeScript {@code GoalTargetType} 1:1.
 */
@RegisterForReflection
public enum GoalTargetType {
    OPEN("goalTargetType.open"),
    AMOUNT("goalTargetType.amount"),
    RELATIVE("goalTargetType.relative"),
    TIME("goalTargetType.time");

    private final String value;

    /** Binds the constant to its JSON wire token. */
    GoalTargetType(String value) {
        this.value = value;
    }

    /** This constant's JSON wire token (the {@code @JsonValue} form). */
    @JsonValue
    public String getValue() {
        return value;
    }

    /** Resolves the constant whose wire token is {@code value}, or throws {@code IllegalArgumentException}. */
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
