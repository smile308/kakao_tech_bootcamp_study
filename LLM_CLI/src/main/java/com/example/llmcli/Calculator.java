// 기본 사칙연산과 괄호를 처리하는 안전한 수식 파서다.
package com.example.llmcli;

public final class Calculator {

    public double evaluate(String expression) {
        if (expression == null || expression.isBlank()) {
            throw new IllegalArgumentException("계산할 수식이 비어 있습니다.");
        }

        Parser parser = new Parser(expression);
        double result = parser.parseExpression();
        parser.skipWhitespace();

        if (!parser.isAtEnd()) {
            throw parser.error("허용하지 않은 문자가 있습니다.");
        }
        if (!Double.isFinite(result)) {
            throw new IllegalArgumentException("계산 결과가 유효한 숫자 범위를 벗어났습니다.");
        }

        return result;
    }

    private static final class Parser {
        private final String input;
        private int position;

        private Parser(String input) {
            this.input = input;
        }

        private double parseExpression() {
            double result = parseTerm();
            while (true) {
                skipWhitespace();
                if (match('+')) {
                    result += parseTerm();
                } else if (match('-')) {
                    result -= parseTerm();
                } else {
                    return result;
                }
            }
        }

        private double parseTerm() {
            double result = parseUnary();
            while (true) {
                skipWhitespace();
                if (match('*')) {
                    result *= parseUnary();
                } else if (match('/')) {
                    double divisor = parseUnary();
                    if (divisor == 0.0) {
                        throw error("0으로 나눌 수 없습니다.");
                    }
                    result /= divisor;
                } else {
                    return result;
                }
            }
        }

        private double parseUnary() {
            skipWhitespace();
            if (match('+')) {
                return parseUnary();
            }
            if (match('-')) {
                return -parseUnary();
            }
            return parsePrimary();
        }

        private double parsePrimary() {
            skipWhitespace();
            if (match('(')) {
                double result = parseExpression();
                skipWhitespace();
                if (!match(')')) {
                    throw error("닫는 괄호가 없습니다.");
                }
                return result;
            }

            return parseNumber();
        }

        private double parseNumber() {
            skipWhitespace();
            int start = position;
            boolean hasDigit = false;

            while (!isAtEnd() && Character.isDigit(current())) {
                hasDigit = true;
                position++;
            }

            if (!isAtEnd() && current() == '.') {
                position++;
                while (!isAtEnd() && Character.isDigit(current())) {
                    hasDigit = true;
                    position++;
                }
            }

            if (!hasDigit) {
                throw error("숫자가 필요합니다.");
            }

            try {
                return Double.parseDouble(input.substring(start, position));
            } catch (NumberFormatException exception) {
                throw error("숫자 형식이 올바르지 않습니다.");
            }
        }

        private boolean match(char expected) {
            if (!isAtEnd() && current() == expected) {
                position++;
                return true;
            }
            return false;
        }

        private char current() {
            return input.charAt(position);
        }

        private boolean isAtEnd() {
            return position >= input.length();
        }

        private void skipWhitespace() {
            while (!isAtEnd() && Character.isWhitespace(current())) {
                position++;
            }
        }

        private IllegalArgumentException error(String message) {
            return new IllegalArgumentException(message + " 위치: " + position);
        }
    }
}
