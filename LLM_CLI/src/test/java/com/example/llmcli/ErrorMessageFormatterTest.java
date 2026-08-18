// CLI가 API·네트워크·일반 오류를 사용자 설명으로 바꾸는 테스트다.
package com.example.llmcli;

import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ErrorMessageFormatterTest {

    @Test
    void explainsNetworkFailureWithoutExposingExceptionDetails() {
        assertEquals(
                "네트워크 오류로 요청하지 못했습니다. 연결 상태를 확인해 주세요.",
                ErrorMessageFormatter.describe(new IOException("secret-looking detail"))
        );
    }

    @Test
    void explainsUnexpectedFailureWithoutExposingExceptionDetails() {
        assertEquals(
                "요청 중 예상하지 못한 오류가 발생했습니다.",
                ErrorMessageFormatter.describe(new IllegalStateException("secret-looking detail"))
        );
    }
}
