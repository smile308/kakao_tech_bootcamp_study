// 계산기 수식 파서의 정상·오류 동작을 검증한다.
package com.example.llmcli;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CalculatorTest {

    private final Calculator calculator = new Calculator();

    @Test
    void multiplication_has_higher_precedence_than_addition() {
        assertEquals(14.0, calculator.evaluate("2 + 3 * 4"));
    }

    @Test
    void parentheses_change_calculation_order() {
        assertEquals(20.0, calculator.evaluate("(2 + 3) * 4"));
    }

    @Test
    void invalid_expression_is_rejected() {
        assertThrows(IllegalArgumentException.class, () -> calculator.evaluate("2 + nope"));
    }

    @Test
    void division_by_zero_is_rejected() {
        assertThrows(IllegalArgumentException.class, () -> calculator.evaluate("10 / 0"));
    }
}
