// 모델이 필요할 때 선택해 호출하는 기본 계산기 도구다.
package com.example.llmcli;

import com.fasterxml.jackson.annotation.JsonClassDescription;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;

@JsonClassDescription("기본 사칙연산과 괄호를 계산한다. 복잡한 프로그래밍이나 단위 변환에는 사용하지 않는다.")
public final class CalculatorTool {

    @JsonPropertyDescription("계산할 수식. +, -, *, /, 괄호와 소수를 사용할 수 있다.")
    public String expression;

    public CalculatorResult execute() {
        String requestedExpression = expression == null ? "" : expression.trim();
        try {
            double value = new Calculator().evaluate(requestedExpression);
            return CalculatorResult.success(requestedExpression, value);
        } catch (IllegalArgumentException exception) {
            return CalculatorResult.failure(requestedExpression, exception.getMessage());
        }
    }
}
