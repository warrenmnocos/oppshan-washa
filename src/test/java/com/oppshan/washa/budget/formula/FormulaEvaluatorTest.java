package com.oppshan.washa.budget.formula;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Map;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

class FormulaEvaluatorTest {

    private final FormulaEvaluator evaluator = new FormulaEvaluator();

    private BigDecimal evaluate(String expression, Map<String, BigDecimal> scope) {
        final var result = evaluator.evaluate(expression, scope);
        assertThat("error for: " + expression, result.error(), is(nullValue()));
        return result.value();
    }

    @Test
    void shouldRespectArithmeticPrecedenceAndParentheses() {
        assertThat(evaluate("2 + 3 * 4", Map.of()), is(comparesEqualTo(new BigDecimal("14"))));
        assertThat(evaluate("(2 + 3) * 4", Map.of()), is(comparesEqualTo(new BigDecimal("20"))));
        assertThat(evaluate("-3 + 5", Map.of()), is(comparesEqualTo(new BigDecimal("2"))));
    }

    @Test
    void shouldYieldZeroOnDivisionByZero() {
        assertThat(evaluate("5 / 0", Map.of()), is(comparesEqualTo(new BigDecimal("0"))));
    }

    @Test
    void shouldEvaluateFunctions() {
        assertThat(evaluate("max(1, 7, 3)", Map.of()), is(comparesEqualTo(new BigDecimal("7"))));
        assertThat(evaluate("min(1, 7, 3)", Map.of()), is(comparesEqualTo(new BigDecimal("1"))));
        assertThat(evaluate("abs(-4)", Map.of()), is(comparesEqualTo(new BigDecimal("4"))));
        assertThat(evaluate("trunc(3.9)", Map.of()), is(comparesEqualTo(new BigDecimal("3"))));
        assertThat(evaluate("clamp(12, 0, 10)", Map.of()), is(comparesEqualTo(new BigDecimal("10"))));
        assertThat(evaluate("floor(1234, 1000)", Map.of()), is(comparesEqualTo(new BigDecimal("1000"))));
        assertThat(evaluate("ceil(1234, 1000)", Map.of()), is(comparesEqualTo(new BigDecimal("2000"))));
        assertThat(evaluate("round(1499, 1000)", Map.of()), is(comparesEqualTo(new BigDecimal("1000"))));
        assertThat(evaluate("round(1500, 1000)", Map.of()), is(comparesEqualTo(new BigDecimal("2000"))));
        assertThat(evaluate("floor(3.7)", Map.of()), is(comparesEqualTo(new BigDecimal("3"))));
    }

    @Test
    void shouldResolveIdentifiersCaseInsensitively() {
        assertThat(evaluate("Gross * 2", Map.of("gross", new BigDecimal("100"))),
                is(comparesEqualTo(new BigDecimal("200"))));
    }

    @Test
    void shouldReturnLastStatementWithLocalAssignments() {
        final var formula = "a = 10\n b = a * 2 \n a + b";
        assertThat(evaluate(formula, Map.of()), is(comparesEqualTo(new BigDecimal("30"))));
    }

    @Test
    void shouldReturnErrorForUnknownIdentifier() {
        final var result = evaluator.evaluate("nope + 1", Map.of());
        assertThat(result.error(), is(notNullValue()));
        assertThat(result.value(), is(comparesEqualTo(new BigDecimal("0"))));
    }

    @Test
    void shouldReturnErrorForUnknownFunction() {
        assertThat(evaluator.evaluate("bogus(1)", Map.of()).error(), is(notNullValue()));
    }

    @Test
    void shouldReturnErrorOnTrailingTokens() {
        assertThat(evaluator.evaluate("1 2", Map.of()).error(), is(notNullValue()));
    }

    @Test
    void shouldReturnErrorOnUnexpectedCharacter() {
        assertThat(evaluator.evaluate("1 & 2", Map.of()).error(), is(notNullValue()));
    }

    @Test
    void shouldTreatBlankAndEmptyStatementsAsZero() {
        assertThat(evaluate("", Map.of()), is(comparesEqualTo(new BigDecimal("0"))));
        assertThat(evaluate(";;", Map.of()), is(comparesEqualTo(new BigDecimal("0"))));
    }

    @Test
    void shouldSupportUnaryPlusAndNestedCalls() {
        assertThat(evaluate("+5", Map.of()), is(comparesEqualTo(new BigDecimal("5"))));
        assertThat(evaluate("max(min(2,9), 3)", Map.of()), is(comparesEqualTo(new BigDecimal("3"))));
    }

    @Test
    void shouldRoundToStepOfOneByDefault() {
        assertThat(evaluate("round(2.4)", Map.of()), is(comparesEqualTo(new BigDecimal("2"))));
        assertThat(evaluate("round(2.5)", Map.of()), is(comparesEqualTo(new BigDecimal("3"))));
    }

    @Test
    void shouldReturnValueUnchangedWhenStepIsZero() {
        assertThat(evaluate("floor(7, 0)", Map.of()), is(comparesEqualTo(new BigDecimal("7"))));
    }
}
