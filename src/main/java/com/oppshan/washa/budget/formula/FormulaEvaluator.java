package com.oppshan.washa.budget.formula;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Safe expression evaluator for the salary engine (HANDOVER §5). No {@code eval}/scripting:
 * a hand-written lexer/parser plus this tree-walker. Supports {@code + - * /} with precedence,
 * parentheses, unary {@code ±}, the functions {@code min/max/abs/trunc/clamp/floor/ceil/round}
 * ({@code floor/ceil/round} take an optional step), case-insensitive identifiers resolved from a
 * scope, and multi-statement formulas (split on {@code ;}/newline, {@code name = expr} assigns a
 * local, the last statement is the result). Division by zero yields 0; any error is returned as a
 * {@link FormulaResult} with a message rather than thrown.
 */
public class FormulaEvaluator {

    private static final MathContext MATH_CONTEXT = new MathContext(34, RoundingMode.HALF_UP);

    public FormulaResult evaluate(String formula,
                                  Map<String, BigDecimal> scope) {
        try {
            final var locals = new HashMap<String, BigDecimal>();
            scope.forEach((name, value) -> locals.put(name.toLowerCase(), value));

            var result = BigDecimal.ZERO;
            for (final var rawStatement : formula.split("[;\\n]")) {
                final var statement = rawStatement.trim();
                if (statement.isEmpty()) {
                    continue;
                }

                final var assignmentIndex = assignmentIndex(statement);
                if (assignmentIndex >= 0) {
                    final var name = statement.substring(0, assignmentIndex).trim().toLowerCase();
                    result = evaluateExpression(statement.substring(assignmentIndex + 1), locals);
                    locals.put(name, result);
                } else {
                    result = evaluateExpression(statement, locals);
                }
            }

            return FormulaResult.ok(result);
        } catch (RuntimeException ex) {
            return FormulaResult.error(ex.getMessage());
        }
    }

    // An '=' is an assignment only when the left side is a bare identifier (no operators).
    private int assignmentIndex(String statement) {
        final var index = statement.indexOf('=');
        if (index <= 0) {
            return -1;
        }

        final var lhs = statement.substring(0, index).trim();
        return lhs.matches("[A-Za-z_][A-Za-z0-9_]*") ? index : -1;
    }

    private BigDecimal evaluateExpression(String expression,
                                          Map<String, BigDecimal> scope) {
        final var parser = new Parser(Lexer.lex(expression));
        final var node = parser.parseExpression();
        if (!parser.atEnd()) {
            throw new IllegalArgumentException("Trailing tokens in: " + expression);
        }

        return evaluate(node, scope);
    }

    private BigDecimal evaluate(Node node,
                                Map<String, BigDecimal> scope) {
        return switch (node) {
            case Node.Num number -> number.value();
            case Node.Var variable -> {
                final var value = scope.get(variable.name());
                if (value == null) {
                    throw new IllegalArgumentException("Unknown identifier: " + variable.name());
                }

                yield value;
            }
            case Node.Unary unary -> {
                final var operand = evaluate(unary.operand(), scope);
                yield unary.op() == '-' ? operand.negate() : operand;
            }
            case Node.Binary binary -> {
                final var left = evaluate(binary.left(), scope);
                final var right = evaluate(binary.right(), scope);
                yield switch (binary.op()) {
                    case '+' -> left.add(right);
                    case '-' -> left.subtract(right);
                    case '*' -> left.multiply(right);
                    case '/' -> right.signum() == 0 ? BigDecimal.ZERO : left.divide(right, MATH_CONTEXT);
                    default -> throw new IllegalArgumentException("Bad operator");
                };
            }
            case Node.Call call -> callFunction(call.name(),
                    call.args().stream().map(argument -> evaluate(argument, scope)).toList());
        };
    }

    private BigDecimal callFunction(String name,
                                    List<BigDecimal> arguments) {
        return switch (name) {
            case "min" -> arguments.stream().min(BigDecimal::compareTo).orElse(BigDecimal.ZERO);
            case "max" -> arguments.stream().max(BigDecimal::compareTo).orElse(BigDecimal.ZERO);
            case "abs" -> arguments.getFirst().abs();
            case "trunc" -> arguments.getFirst().setScale(0, RoundingMode.DOWN);
            case "clamp" -> arguments.getFirst().max(arguments.get(1)).min(arguments.get(2));
            case "floor" -> roundToStep(arguments, RoundingMode.FLOOR);
            case "ceil" -> roundToStep(arguments, RoundingMode.CEILING);
            case "round" -> roundToStep(arguments, RoundingMode.HALF_UP);
            default -> throw new IllegalArgumentException("Unknown function: " + name);
        };
    }

    // floor/ceil/round(x[, step]) — round x to a multiple of step (default 1).
    private BigDecimal roundToStep(List<BigDecimal> arguments,
                                   RoundingMode mode) {
        final var value = arguments.getFirst();
        final var step = arguments.size() > 1 ? arguments.get(1) : BigDecimal.ONE;
        if (step.signum() == 0) {
            return value;
        }

        return value.divide(step, 0, mode).multiply(step);
    }
}
