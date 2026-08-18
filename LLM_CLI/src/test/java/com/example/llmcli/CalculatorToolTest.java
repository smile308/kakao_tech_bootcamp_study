// 모델이 호출하는 계산기 도구의 결과 형식을 검증한다.
package com.example.llmcli;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CalculatorToolTest {

    @Test
    void returns_calculation_result_for_valid_expression() {
        CalculatorTool tool = new CalculatorTool();
        tool.expression = "2 + 3 * 4";

        CalculatorResult result = tool.execute();

        assertTrue(result.success());
        assertEquals(14.0, result.value());
        assertEquals("2 + 3 * 4", result.expression());
    }

    @Test
    void returns_explanation_for_invalid_expression() {
        CalculatorTool tool = new CalculatorTool();
        tool.expression = "10 / 0";

        CalculatorResult result = tool.execute();

        assertFalse(result.success());
        assertEquals("10 / 0", result.expression());
        assertTrue(result.error().contains("나눌 수"));
    }
}
