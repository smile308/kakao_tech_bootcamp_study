// 모델의 함수 호출 이름과 JSON 인자를 로컬 도구 실행으로 연결하는 테스트다.
package com.example.llmcli;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ToolCallHandlerTest {

    private final ToolCallHandler handler = new ToolCallHandler();

    @Test
    void calculatorFunctionCallReturnsCalculatedValue() {
        CalculatorResult result = handler.execute("CalculatorTool", "{\"expression\":\"2 + 3 * 4\"}");

        assertTrue(result.success());
        assertEquals(14.0, result.value());
    }

    @Test
    void invalidCalculatorArgumentsReturnToolError() {
        CalculatorResult result = handler.execute("CalculatorTool", "{\"expression\":\"1 / 0\"}");

        assertFalse(result.success());
        assertTrue(result.error().contains("0으로 나눌 수 없습니다"));
    }

    @Test
    void unknownToolReturnsExplainableError() {
        CalculatorResult result = handler.execute("UnknownTool", "{}");

        assertFalse(result.success());
        assertTrue(result.error().contains("알 수 없는 도구"));
    }
}
