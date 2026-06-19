package com.oppshan.washa.budget.formula;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/** Recursive-descent parser: expression -> term -> factor -> primary, into a {@link Node} AST. */
final class Parser {

    private final List<Lexer.Token> tokens;
    private int position;

    Parser(List<Lexer.Token> tokens) {
        this.tokens = tokens;
    }

    Node parseExpression() {
        var node = parseTerm();
        while (isOperator("+") || isOperator("-")) {
            final var operator = next().text().charAt(0);
            node = new Node.Binary(operator, node, parseTerm());
        }

        return node;
    }

    boolean atEnd() {
        return peek().type() == Lexer.Type.EOF;
    }

    private Node parseTerm() {
        var node = parseFactor();
        while (isOperator("*") || isOperator("/")) {
            final var operator = next().text().charAt(0);
            node = new Node.Binary(operator, node, parseFactor());
        }

        return node;
    }

    private Node parseFactor() {
        if (isOperator("+") || isOperator("-")) {
            final var operator = next().text().charAt(0);
            return new Node.Unary(operator, parseFactor());
        }

        return parsePrimary();
    }

    private Node parsePrimary() {
        final var token = peek();
        switch (token.type()) {
            case NUMBER -> {
                next();
                return new Node.Num(new BigDecimal(token.text()));
            }
            case LPAREN -> {
                next();
                final var inner = parseExpression();
                expect(Lexer.Type.RPAREN);
                return inner;
            }
            case IDENT -> {
                next();
                if (peek().type() == Lexer.Type.LPAREN) {
                    return parseCall(token.text());
                }

                return new Node.Var(token.text().toLowerCase());
            }
            default -> throw new IllegalArgumentException("Unexpected token: " + token.text());
        }
    }

    private Node parseCall(String name) {
        next(); // consume '('
        final var arguments = new ArrayList<Node>();
        if (peek().type() != Lexer.Type.RPAREN) {
            arguments.add(parseExpression());
            while (peek().type() == Lexer.Type.COMMA) {
                next();
                arguments.add(parseExpression());
            }
        }
        expect(Lexer.Type.RPAREN);

        return new Node.Call(name.toLowerCase(), arguments);
    }

    private Lexer.Token peek() {
        return tokens.get(position);
    }

    private Lexer.Token next() {
        return tokens.get(position++);
    }

    private boolean isOperator(String symbol) {
        return peek().type() == Lexer.Type.OP && peek().text().equals(symbol);
    }

    private void expect(Lexer.Type type) {
        if (peek().type() != type) {
            throw new IllegalArgumentException("Expected " + type);
        }

        next();
    }
}
