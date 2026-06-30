package com.oppshan.washa.budget.formula;

import java.util.ArrayList;
import java.util.List;

/** Tokenizes a single formula expression (statement splitting is done by the evaluator). */
final class Lexer {

    enum Type {NUMBER, IDENT, OP, LPAREN, RPAREN, COMMA, EOF}

    record Token(Type type, String text) {
    }

    private Lexer() {
    }

    static List<Token> lex(String source) {
        final var tokens = new ArrayList<Token>();
        final var length = source.length();
        var index = 0;
        while (index < length) {
            final var character = source.charAt(index);
            if (Character.isWhitespace(character)) {
                index++;
                continue;
            }
            if (Character.isDigit(character) || character == '.') {
                final var start = index;
                while (index < length
                        && (Character.isDigit(source.charAt(index)) || source.charAt(index) == '.')) {
                    index++;
                }
                tokens.add(new Token(Type.NUMBER, source.substring(start, index)));
                continue;
            }
            if (Character.isLetter(character) || character == '_') {
                final var start = index;
                while (index < length
                        && (Character.isLetterOrDigit(source.charAt(index)) || source.charAt(index) == '_')) {
                    index++;
                }
                tokens.add(new Token(Type.IDENT, source.substring(start, index)));
                continue;
            }
            switch (character) {
                case '+', '-', '*', '/' -> tokens.add(new Token(Type.OP, String.valueOf(character)));
                case '(' -> tokens.add(new Token(Type.LPAREN, "("));
                case ')' -> tokens.add(new Token(Type.RPAREN, ")"));
                case ',' -> tokens.add(new Token(Type.COMMA, ","));
                default -> throw new IllegalArgumentException("Unexpected character: " + character);
            }
            index++;
        }
        tokens.add(new Token(Type.EOF, ""));
        return tokens;
    }
}
