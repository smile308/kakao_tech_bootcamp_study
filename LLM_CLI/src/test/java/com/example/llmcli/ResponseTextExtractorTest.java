// Responses API 메시지 출력에서 최종 텍스트를 추출하는 테스트다.
package com.example.llmcli;

import com.openai.models.responses.ResponseOutputItem;
import com.openai.models.responses.ResponseOutputMessage;
import com.openai.models.responses.ResponseOutputText;
import com.openai.models.responses.ResponseOutputMessage.Status;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ResponseTextExtractorTest {

    @Test
    void extractsAllOutputTextFromResponseMessages() {
        List<ResponseOutputItem> responseOutput = List.of(
                ResponseOutputItem.ofMessage(ResponseOutputMessage.builder()
                        .id("message-test")
                        .status(Status.COMPLETED)
                        .addContent(ResponseOutputText.builder().text("첫 문장").annotations(List.of()).build())
                        .addContent(ResponseOutputText.builder().text("둘째 문장").annotations(List.of()).build())
                        .build())
        );

        assertEquals("첫 문장둘째 문장", ResponseTextExtractor.extract(responseOutput));
    }
}
