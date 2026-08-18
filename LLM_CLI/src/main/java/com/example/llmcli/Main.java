// API 키를 준비하고 대화·응답·저장을 반복하는 CLI 진입점이다.
package com.example.llmcli;

import com.openai.client.OpenAIClient;
import com.openai.client.okhttp.OpenAIOkHttpClient;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class Main {

    private Main() {
    }

    public static void main(String[] args) {
        Path projectRoot = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize();
        Optional<String> apiKey = EnvFileLoader.loadApiKey(projectRoot.resolve(".env.local"));
        if (apiKey.isEmpty()) {
            System.err.println("API 키를 찾지 못했습니다. 환경변수 OPENROUTER_API_KEY 또는 .env.local을 확인해 주세요.");
            return;
        }

        ConversationStore conversationStore = new ConversationStore(
                projectRoot.resolve("data/conversation.json")
        );
        ConversationStore.LoadResult loaded = conversationStore.load();
        loaded.warning().ifPresent(System.err::println);
        List<ConversationMessage> history = new ArrayList<>(loaded.messages());

        OpenAIClient client = OpenAIOkHttpClient.builder()
                .baseUrl("https://openrouter.ai/api/v1")
                .apiKey(apiKey.get())
                .build();

        try {
            ChatService chatService = new ChatService(client, message -> System.out.println("tool> " + message));
            runLoop(chatService, conversationStore, history);
        } finally {
            client.close();
        }
    }

    private static void runLoop(
            ChatService chatService,
            ConversationStore conversationStore,
            List<ConversationMessage> history
    ) {
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(System.in, StandardCharsets.UTF_8)
        )) {
            System.out.println("LLM CLI를 시작했습니다. 종료하려면 exit 또는 quit를 입력하세요.");

            while (true) {
                System.out.print("you> ");
                System.out.flush();
                String userInput = reader.readLine();
                if (userInput == null) {
                    System.out.println();
                    return;
                }

                String trimmedInput = userInput.trim();
                if (trimmedInput.equalsIgnoreCase("exit") || trimmedInput.equalsIgnoreCase("quit")) {
                    return;
                }
                if (trimmedInput.isEmpty()) {
                    continue;
                }

                try {
                    String answer = chatService.reply(history, userInput);
                    System.out.println("assistant> " + answer);
                    history.add(ConversationMessage.user(userInput));
                    history.add(ConversationMessage.assistant(answer));
                    conversationStore.save(List.copyOf(history)).ifPresent(
                            warning -> System.err.println("경고> " + warning)
                    );
                } catch (Exception exception) {
                    System.out.println("assistant> " + ErrorMessageFormatter.describe(exception));
                }
            }
        } catch (IOException exception) {
            System.err.println("CLI 입력을 읽지 못했습니다. 프로그램을 종료합니다.");
        }
    }
}
