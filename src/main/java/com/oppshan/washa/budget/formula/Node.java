package com.oppshan.washa.budget.formula;

import java.math.BigDecimal;
import java.util.List;

/** AST node for a parsed formula expression. */
sealed interface Node {

    record Num(BigDecimal value) implements Node {
    }

    record Var(String name) implements Node {
    }

    record Unary(char op, Node operand) implements Node {
    }

    record Binary(char op, Node left, Node right) implements Node {
    }

    record Call(String name, List<Node> args) implements Node {
    }
}
