package com.oppshan.washa.budget.formula;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Recursive-descent parser turning a {@link Lexer} token list into a {@link Node} AST. The descent
 * levels encode operator precedence: {@code parseExpression} handles {@code + -}, {@code parseTerm} the
 * tighter-binding {@code * /}, {@code parseFactor} a leading unary {@code + -}, and
 * {@code parsePrimary} the atoms (numbers, parenthesized sub-expressions, and identifiers or function
 * calls). It consumes one expression; the evaluator calls {@link #atEnd()} afterwards to reject
 * anything left over.
 */
final class Parser {

    /** The token stream to parse, ending in an {@code EOF} token. */
    private final List<Lexer.Token> tokens;
    /** Cursor into {@code tokens}. */
    private int position;

    /** Wraps a lexed token stream; the cursor starts at the first token. */
    Parser(List<Lexer.Token> tokens) {
        this.tokens = tokens;
    }

    /** Lowest precedence: a run of terms joined by {@code +} / {@code -}, left-associative. */
    Node parseExpression() {
        var node = parseTerm();
        while (isOperator("+") || isOperator("-")) {
            final var operator = next().text().charAt(0);
            node = new Node.Binary(operator, node, parseTerm());
        }

        return node;
    }

    /** Whether the cursor has reached the {@code EOF} token (nothing left to parse). */
    boolean atEnd() {
        return peek().type() == Lexer.Type.EOF;
    }

    /** Next precedence up: a run of factors joined by {@code *} / {@code /}, left-associative. */
    private Node parseTerm() {
        var node = parseFactor();
        while (isOperator("*") || isOperator("/")) {
            final var operator = next().text().charAt(0);
            node = new Node.Binary(operator, node, parseFactor());
        }

        return node;
    }

    /** A leading unary {@code +} / {@code -} (recursing, so repeated signs nest), otherwise a primary. */
    private Node parseFactor() {
        if (isOperator("+") || isOperator("-")) {
            final var operator = next().text().charAt(0);
            return new Node.Unary(operator, parseFactor());
        }

        return parsePrimary();
    }

    /**
     * An atom: a number literal, a parenthesized sub-expression, or an identifier, which becomes a
     * function {@link Node.Call} when the next token is {@code (}, otherwise a {@link Node.Var}.
     */
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

    /**
     * Parses {@code name(arg, arg, ...)} once the identifier is seen, consuming the opening {@code (}
     * first: zero or more comma-separated argument expressions up to the closing {@code )}. The name is
     * lower-cased for case-insensitive function dispatch.
     */
    private Node parseCall(String name) {
        next();
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

    /** The token at the cursor, without advancing. */
    private Lexer.Token peek() {
        return tokens.get(position);
    }

    /** The token at the cursor, then advances past it. */
    private Lexer.Token next() {
        return tokens.get(position++);
    }

    /** Whether the cursor sits on an {@code OP} token matching {@code symbol}. */
    private boolean isOperator(String symbol) {
        return peek().type() == Lexer.Type.OP && peek().text().equals(symbol);
    }

    /**
     * Consumes the current token, requiring it to be {@code type}.
     *
     * @throws IllegalArgumentException if the current token isn't {@code type}
     */
    private void expect(Lexer.Type type) {
        if (peek().type() != type) {
            throw new IllegalArgumentException("Expected " + type);
        }

        next();
    }
}
