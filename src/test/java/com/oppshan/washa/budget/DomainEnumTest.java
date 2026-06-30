package com.oppshan.washa.budget;

import com.oppshan.washa.budget.formula.FormulaEvaluator;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Map;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.comparesEqualTo;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** fromValue round-trips and per-constant behaviour for the domain enums (pure logic, no Quarkus boot). */
class DomainEnumTest {

    private final FormulaEvaluator evaluator = new FormulaEvaluator();

    @Test
    void bracketOpRoundTripsAndEvaluatesEachOperator() {
        for (final var op : BracketOp.values()) {
            assertThat(BracketOp.fromValue(op.getValue()), is(op));
        }
        assertThrows(IllegalArgumentException.class, () -> BracketOp.fromValue("nope"));

        // holds() reads the sign of lhs.compareTo(rhs): >0, 0, <0.
        assertThat(BracketOp.GT.holds(1), is(true));
        assertThat(BracketOp.GT.holds(0), is(false));
        assertThat(BracketOp.GTE.holds(0), is(true));
        assertThat(BracketOp.GTE.holds(-1), is(false));
        assertThat(BracketOp.LT.holds(-1), is(true));
        assertThat(BracketOp.LT.holds(0), is(false));
        assertThat(BracketOp.LTE.holds(0), is(true));
        assertThat(BracketOp.LTE.holds(1), is(false));
        assertThat(BracketOp.EQ.holds(0), is(true));
        assertThat(BracketOp.EQ.holds(1), is(false));
    }

    @Test
    void bracketTypeRoundTripsAndContributesPerType() {
        for (final var type : BracketType.values()) {
            assertThat(BracketType.fromValue(type.getValue()), is(type));
        }
        assertThrows(IllegalArgumentException.class, () -> BracketType.fromValue("nope"));

        final var scope = Map.of("gross", new BigDecimal("1000"), "basic", new BigDecimal("800"));
        assertThat(BracketType.FIXED.contribution(new BigDecimal("5"), null, scope, evaluator), comparesEqualTo(new BigDecimal("5")));
        assertThat(BracketType.FIXED.contribution(null, null, scope, evaluator), comparesEqualTo(BigDecimal.ZERO));
        assertThat(BracketType.FORMULA.contribution(null, null, scope, evaluator), comparesEqualTo(BigDecimal.ZERO));
        assertThat(BracketType.PCTGROSS.contribution(new BigDecimal("10"), null, scope, evaluator), comparesEqualTo(new BigDecimal("100")));
        assertThat(BracketType.PCTBASIC.contribution(new BigDecimal("10"), null, scope, evaluator), comparesEqualTo(new BigDecimal("80")));
        assertThat(BracketType.PCTGROSS.contribution(null, null, scope, evaluator), comparesEqualTo(BigDecimal.ZERO));
    }

    @Test
    void valueEnumsRoundTripAndRejectUnknown() {
        for (final var t : VariableType.values()) {
            assertThat(VariableType.fromValue(t.getValue()), is(t));
        }
        assertThrows(IllegalArgumentException.class, () -> VariableType.fromValue("nope"));

        for (final var t : DeductionType.values()) {
            assertThat(DeductionType.fromValue(t.getValue()), is(t));
        }
        assertThrows(IllegalArgumentException.class, () -> DeductionType.fromValue("nope"));

        for (final var b : DeductionBase.values()) {
            assertThat(DeductionBase.fromValue(b.getValue()), is(b));
        }
        assertThrows(IllegalArgumentException.class, () -> DeductionBase.fromValue("nope"));

        for (final var t : GoalTargetType.values()) {
            assertThat(GoalTargetType.fromValue(t.getValue()), is(t));
        }
        assertThrows(IllegalArgumentException.class, () -> GoalTargetType.fromValue("nope"));

        for (final var m : DebtRepriceMode.values()) {
            assertThat(DebtRepriceMode.fromValue(m.getValue()), is(m));
        }
        assertThrows(IllegalArgumentException.class, () -> DebtRepriceMode.fromValue("nope"));
    }
}
