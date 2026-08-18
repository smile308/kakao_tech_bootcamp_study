// API 요청에 포함할 최근 대화 범위를 계산한다.
package com.example.llmcli;

import java.util.List;

public final class ConversationWindow {

    private ConversationWindow() {
    }

    public static List<ConversationMessage> latestPairs(
            List<ConversationMessage> messages,
            int pairCount
    ) {
        if (pairCount < 0) {
            throw new IllegalArgumentException("기억할 대화 수는 음수가 될 수 없습니다.");
        }

        int messageLimit = pairCount * 2;
        int startIndex = Math.max(0, messages.size() - messageLimit);
        return List.copyOf(messages.subList(startIndex, messages.size()));
    }
}
