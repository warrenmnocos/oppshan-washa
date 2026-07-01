package com.oppshan.washa.budget.formula;

import java.math.BigDecimal;
import java.util.List;

/**
 * One node of the AST a {@link Parser} builds from a formula expression. Sealed so
 * {@code FormulaEvaluator}'s tree-walking switch stays exhaustive without a default branch: add a
 * variant here and the evaluator won't compile until it handles it.
 */
sealed interface Node {

    /** A numeric literal. */
    record Num(BigDecimal value) implements Node {
    }

    /** An identifier reference; {@code name} is already lower-cased for case-insensitive lookup. */
    record Var(String name) implements Node {
    }

    /** A unary {@code +} or {@code -} applied to one operand. */
    record Unary(char op, Node operand) implements Node {
    }

    /** A binary {@code + - * /} over two operands. */
    record Binary(char op, Node left, Node right) implements Node {
    }

    /** A function call; {@code name} is already lower-cased and {@code args} are the argument subtrees. */
    record Call(String name, List<Node> args) implements Node {
    }
}
