// 최근 대화와 현재 입력을 Responses API에 보내고 함수 호출 결과를 연결한다.
package com.example.llmcli;

import com.openai.client.OpenAIClient;
import com.openai.core.JsonSchemaLocalValidation;
import com.openai.core.JsonValue;
import com.openai.models.ChatModel;
import com.openai.models.Reasoning;
import com.openai.models.ReasoningEffort;
import com.openai.models.responses.Response;
import com.openai.models.responses.ResponseCreateParams;
import com.openai.models.responses.ResponseFunctionToolCall;
import com.openai.models.responses.ResponseInputItem;
import com.openai.models.responses.ResponseOutputMessage;
import com.openai.models.responses.ResponseOutputText;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

public final class ChatService {

    private static final int MAX_HISTORY_PAIRS = 10;
    private static final int MAX_TOOL_CALLS = 5;

    private final OpenAIClient client;
    private final ToolCallHandler toolCallHandler;
    private final Consumer<String> traceLogger;

    public ChatService(OpenAIClient client, Consumer<String> traceLogger) {
        this.client = Objects.requireNonNull(client, "client");
        this.toolCallHandler = new ToolCallHandler();
        this.traceLogger = Objects.requireNonNull(traceLogger, "traceLogger");
    }

    public String reply(List<ConversationMessage> history, String userInput) {
        List<ResponseInputItem> inputs = toInputItems(ConversationWindow.latestPairs(history, MAX_HISTORY_PAIRS));
        inputs.add(toInputMessage(ResponseInputItem.Message.Role.USER, userInput));

        int toolCallCount = 0;
        Response response = createResponse(inputs);

        while (true) {
            List<ResponseFunctionToolCall> functionCalls = response.output().stream()
                    .filter(item -> item.isFunctionCall())
                    .map(item -> item.asFunctionCall())
                    .toList();

            if (functionCalls.isEmpty()) {
                String responseText = ResponseTextExtractor.extract(response.output());
                if (responseText.isBlank()) {
                    throw new IllegalStateException("모델 응답에 표시할 텍스트가 없습니다.");
                }
                return responseText;
            }

            if (toolCallCount + functionCalls.size() > MAX_TOOL_CALLS) {
                throw new IllegalStateException("도구 호출이 너무 많아 이번 요청을 중단했습니다.");
            }

            for (var outputItem : response.output()) {
                if (outputItem.isReasoning()) {
                    inputs.add(ResponseInputItem.ofReasoning(outputItem.asReasoning()));
                } else if (outputItem.isFunctionCall()) {
                    inputs.add(ResponseInputItem.ofFunctionCall(outputItem.asFunctionCall()));
                }
            }

            for (ResponseFunctionToolCall functionCall : functionCalls) {
                traceLogger.accept("도구 호출: " + functionCall.name());
                CalculatorResult result = toolCallHandler.execute(functionCall.name(), functionCall.arguments());
                inputs.add(ResponseInputItem.ofFunctionCallOutput(
                        ResponseInputItem.FunctionCallOutput.builder()
                                .callId(functionCall.callId())
                                .outputAsJson(result)
                                .build()
                ));
                toolCallCount++;
            }

            response = createResponse(inputs);
        }
    }

    private Response createResponse(List<ResponseInputItem> inputs) {
        ResponseCreateParams parameters = ResponseCreateParams.builder()
                .model(ChatModel.of("openrouter/free"))
                .reasoning(Reasoning.builder().effort(ReasoningEffort.NONE).build())
                .inputOfResponse(inputs)
                .maxToolCalls(MAX_TOOL_CALLS)
                .parallelToolCalls(false)
                .addTool(CalculatorTool.class, JsonSchemaLocalValidation.YES)
                .build();

        return client.responses().create(parameters);
    }

    private List<ResponseInputItem> toInputItems(List<ConversationMessage> messages) {
        List<ResponseInputItem> inputItems = new ArrayList<>(messages.size());
        int assistantMessageIndex = 0;
        for (ConversationMessage message : messages) {
            if (message.role().equals("user")) {
                inputItems.add(toInputMessage(ResponseInputItem.Message.Role.USER, message.content()));
            } else if (message.role().equals("assistant")) {
                inputItems.add(toAssistantInputMessage(message.content(), assistantMessageIndex));
                assistantMessageIndex++;
            } else {
                throw new IllegalArgumentException("지원하지 않는 대화 역할입니다: " + message.role());
            }
        }
        return inputItems;
    }

    private ResponseInputItem toAssistantInputMessage(String content, int messageIndex) {
        return ResponseInputItem.ofResponseOutputMessage(
                ResponseOutputMessage.builder()
                        .id("history-assistant-" + messageIndex)
                        .type(JsonValue.from("message"))
                        .role(JsonValue.from("assistant"))
                        .status(ResponseOutputMessage.Status.COMPLETED)
                        .addContent(ResponseOutputText.builder()
                                .text(content)
                                .annotations(List.of())
                                .build())
                        .build()
        );
    }

    private ResponseInputItem toInputMessage(ResponseInputItem.Message.Role role, String content) {
        return ResponseInputItem.ofMessage(
                ResponseInputItem.Message.builder()
                        .role(role)
                        .addInputTextContent(content)
                        .build()
        );
    }
}
