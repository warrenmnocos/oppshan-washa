package com.oppshan.washa.budget;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import io.quarkus.runtime.annotations.RegisterForReflection;

/**
 * The comparison a tax bracket row runs against its left-hand value. Each constant
 * carries its own test, a strategy method over the sign of {@code lhs.compareTo(rhs)}, so the set has
 * no central switch to keep in step. Constants are UPPER_CASE per Java convention and that's what the
 * relational column stores via {@code @Enumerated(STRING)}; the lowercase {@link #getValue()} is the
 * JSON wire token, mirroring the TypeScript {@code BracketOp}.
 */
@RegisterForReflection
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

    /** Binds the constant to its JSON wire token. */
    BracketOp(String value) {
        this.value = value;
    }

    /** Whether the comparison sign (from {@code lhs.compareTo(rhs)}) satisfies this operator. */
    public abstract boolean holds(int comparison);

    /** This constant's JSON wire token (the {@code @JsonValue} form). */
    @JsonValue
    public String getValue() {
        return value;
    }

    /** Resolves the constant whose wire token is {@code value}, or throws {@code IllegalArgumentException}. */
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
