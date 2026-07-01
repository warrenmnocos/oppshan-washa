package com.oppshan.washa.budget.formula;

import java.util.ArrayList;
import java.util.List;

/** Tokenizes a single formula expression (statement splitting is done by the evaluator). */
final class Lexer {

    /** Token kinds the parser dispatches on; {@code EOF} marks the end of the stream. */
    enum Type {NUMBER, IDENT, OP, LPAREN, RPAREN, COMMA, EOF}

    /** A lexed token: its {@code type} and the exact source {@code text} it matched. */
    record Token(Type type, String text) {
    }

    /** No instances; {@code lex} is the only entry point. */
    private Lexer() {
    }

    /**
     * Scans {@code source} into a flat token list ending in an {@code EOF}. Whitespace is skipped; a run
     * of digits and dots becomes one {@code NUMBER} (shape isn't validated here, so {@code "1.2.3"}
     * lexes as a single token and only {@code new BigDecimal} rejects it downstream); a letter or
     * underscore followed by letters, digits, or underscores becomes an {@code IDENT}; and
     * {@code + - * /}, parentheses, and commas each become their own token.
     *
     * @throws IllegalArgumentException on any other character
     */
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
