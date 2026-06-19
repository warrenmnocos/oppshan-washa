package com.oppshan.washa.budget.formula;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class FormulaEvaluatorTest {

    private final FormulaEvaluator evaluator = new FormulaEvaluator();

    private BigDecimal evaluate(String expression, Map<String, BigDecimal> scope) {
        final var result = evaluator.evaluate(expression, scope);
        assertThat(result.error()).as("error for: " + expression).isNull();
        return result.value();
    }

    @Test
    void shouldRespectArithmeticPrecedenceAndParentheses() {
        assertThat(evaluate("2 + 3 * 4", Map.of())).isEqualByComparingTo("14");
        assertThat(evaluate("(2 + 3) * 4", Map.of())).isEqualByComparingTo("20");
        assertThat(evaluate("-3 + 5", Map.of())).isEqualByComparingTo("2");
    }

    @Test
    void shouldYieldZeroOnDivisionByZero() {
        assertThat(evaluate("5 / 0", Map.of())).isEqualByComparingTo("0");
    }

    @Test
    void shouldEvaluateFunctions() {
        assertThat(evaluate("max(1, 7, 3)", Map.of())).isEqualByComparingTo("7");
        assertThat(evaluate("min(1, 7, 3)", Map.of())).isEqualByComparingTo("1");
        assertThat(evaluate("abs(-4)", Map.of())).isEqualByComparingTo("4");
        assertThat(evaluate("trunc(3.9)", Map.of())).isEqualByComparingTo("3");
        assertThat(evaluate("clamp(12, 0, 10)", Map.of())).isEqualByComparingTo("10");
        assertThat(evaluate("floor(1234, 1000)", Map.of())).isEqualByComparingTo("1000");
        assertThat(evaluate("ceil(1234, 1000)", Map.of())).isEqualByComparingTo("2000");
        assertThat(evaluate("round(1499, 1000)", Map.of())).isEqualByComparingTo("1000");
        assertThat(evaluate("round(1500, 1000)", Map.of())).isEqualByComparingTo("2000");
        assertThat(evaluate("floor(3.7)", Map.of())).isEqualByComparingTo("3");
    }

    @Test
    void shouldResolveIdentifiersCaseInsensitively() {
        assertThat(evaluate("Gross * 2", Map.of("gross", new BigDecimal("100"))))
                .isEqualByComparingTo("200");
    }

    @Test
    void shouldReturnLastStatementWithLocalAssignments() {
        final var formula = "a = 10\n b = a * 2 \n a + b";
        assertThat(evaluate(formula, Map.of())).isEqualByComparingTo("30");
    }

    @Test
    void shouldReturnErrorForUnknownIdentifier() {
        final var result = evaluator.evaluate("nope + 1", Map.of());
        assertThat(result.error()).isNotNull();
        assertThat(result.value()).isEqualByComparingTo("0");
    }
}
