// Responses API 응답의 메시지 콘텐츠에서 사용자에게 보여줄 텍스트를 추출한다.
package com.example.llmcli;

import com.openai.models.responses.ResponseOutputItem;

import java.util.List;

public final class ResponseTextExtractor {

    private ResponseTextExtractor() {
    }

    public static String extract(List<ResponseOutputItem> outputItems) {
        StringBuilder text = new StringBuilder();

        outputItems.stream()
                .filter(item -> item.isMessage())
                .map(item -> item.asMessage())
                .flatMap(message -> message.content().stream())
                .flatMap(content -> content.outputText().stream())
                .map(outputText -> outputText.text())
                .forEach(text::append);

        return text.toString();
    }
}
