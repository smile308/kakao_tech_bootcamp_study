// API 요청과 로컬 JSON에 함께 사용하는 대화 메시지다.
package com.example.llmcli;

import java.util.Objects;

public record ConversationMessage(String role, String content) {

    public ConversationMessage {
        Objects.requireNonNull(role, "role");
        Objects.requireNonNull(content, "content");
        if (!role.equals("user") && !role.equals("assistant")) {
            throw new IllegalArgumentException("지원하지 않는 대화 역할입니다: " + role);
        }
    }

    public static ConversationMessage user(String content) {
        return new ConversationMessage("user", content);
    }

    public static ConversationMessage assistant(String content) {
        return new ConversationMessage("assistant", content);
    }
}
