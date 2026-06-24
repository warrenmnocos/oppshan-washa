package com.oppshan.washa.budget;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * The comparison a tax bracket row applies to its left-hand value (HANDOVER §6). Each constant carries
 * its own test (a strategy method over the {@code lhs.compareTo(rhs)} sign), so the engine dispatches
 * polymorphically rather than switching. The constant is UPPER_CASE per Java convention and is what
 * the relational column stores via @Enumerated(STRING); the lowercase {@link #value()} is the JSON
 * wire string, matching the TypeScript {@code BracketOp}.
 */
public enum BracketOp {
    GT("bracketOp.gt") {
        @Override
        public boolean holds(int comparison) {
            return comparison > 0;
        }
    },
    GTE("bracketOp.gte") {
        @Override
        public boolean holds(int comparison) {
            return comparison >= 0;
        }
    },
    LT("bracketOp.lt") {
        @Override
        public boolean holds(int comparison) {
            return comparison < 0;
        }
    },
    LTE("bracketOp.lte") {
        @Override
        public boolean holds(int comparison) {
            return comparison <= 0;
        }
    },
    EQ("bracketOp.eq") {
        @Override
        public boolean holds(int comparison) {
            return comparison == 0;
        }
    };

    private final String value;

    BracketOp(String value) {
        this.value = value;
    }

    /** Whether the comparison sign (from {@code lhs.compareTo(rhs)}) satisfies this operator. */
    public abstract boolean holds(int comparison);

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
