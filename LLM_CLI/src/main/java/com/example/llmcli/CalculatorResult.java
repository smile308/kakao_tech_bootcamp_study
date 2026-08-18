// 계산기 도구가 모델에 전달하는 성공·실패 결과다.
package com.example.llmcli;

public record CalculatorResult(
        boolean success,
        String expression,
        Double value,
        String error
) {

    public static CalculatorResult success(String expression, double value) {
        return new CalculatorResult(true, expression, value, null);
    }

    public static CalculatorResult failure(String expression, String error) {
        return new CalculatorResult(false, expression, null, error);
    }
}
