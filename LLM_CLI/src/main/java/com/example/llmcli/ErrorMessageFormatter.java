// 외부 요청 오류를 비밀값 없이 CLI 사용자에게 설명할 문장으로 바꾼다.
package com.example.llmcli;

import com.openai.errors.OpenAIIoException;
import com.openai.errors.OpenAIException;
import com.openai.errors.PermissionDeniedException;
import com.openai.errors.RateLimitException;
import com.openai.errors.UnauthorizedException;

import java.io.IOException;
import java.util.Objects;

public final class ErrorMessageFormatter {

    private ErrorMessageFormatter() {
    }

    public static String describe(Throwable throwable) {
        Objects.requireNonNull(throwable, "throwable");

        if (throwable instanceof UnauthorizedException) {
            return "인증 오류입니다. OPENROUTER_API_KEY와 OpenRouter 키 권한을 확인해 주세요.";
        }
        if (throwable instanceof PermissionDeniedException) {
            return "권한 오류입니다. 선택한 모델을 프로젝트에서 사용할 수 있는지 확인해 주세요.";
        }
        if (throwable instanceof RateLimitException) {
            return "사용량 또는 요청 한도 오류입니다. 잠시 후 다시 시도해 주세요.";
        }
        if (throwable instanceof OpenAIIoException || throwable instanceof IOException) {
            return "네트워크 오류로 요청하지 못했습니다. 연결 상태를 확인해 주세요.";
        }
        if (throwable instanceof OpenAIException) {
            return "OpenRouter API 요청에 실패했습니다. 모델과 사용량을 확인해 주세요.";
        }
        return "요청 중 예상하지 못한 오류가 발생했습니다.";
    }
}
