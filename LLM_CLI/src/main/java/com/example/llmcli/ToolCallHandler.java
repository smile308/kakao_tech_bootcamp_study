// 모델이 선택한 함수 호출을 안전한 로컬 도구 실행으로 연결한다.
package com.example.llmcli;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.Locale;

public final class ToolCallHandler {

    private static final String CALCULATOR_TOOL_NAME = "calculatortool";

    private final ObjectMapper objectMapper;

    public ToolCallHandler() {
        this.objectMapper = new ObjectMapper();
    }

    public CalculatorResult execute(String toolName, String argumentsJson) {
        if (!isCalculatorTool(toolName)) {
            return CalculatorResult.failure("", "알 수 없는 도구입니다: " + toolName);
        }

        try {
            CalculatorTool calculatorTool = objectMapper.readValue(argumentsJson, CalculatorTool.class);
            return calculatorTool.execute();
        } catch (JsonProcessingException | RuntimeException exception) {
            return CalculatorResult.failure("", "계산기 인자를 읽지 못했습니다.");
        }
    }

    private boolean isCalculatorTool(String toolName) {
        if (toolName == null) {
            return false;
        }

        String normalizedName = toolName.replaceAll("[^A-Za-z0-9]", "").toLowerCase(Locale.ROOT);
        return CALCULATOR_TOOL_NAME.equals(normalizedName)
                || "calculator".equals(normalizedName)
                || "calculate".equals(normalizedName);
    }
}
