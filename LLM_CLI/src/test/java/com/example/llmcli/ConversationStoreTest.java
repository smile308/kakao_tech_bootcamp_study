// 대화 이력의 JSON 저장·복원과 최근 기억 범위를 검증한다.
package com.example.llmcli;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConversationStoreTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void saves_and_loads_conversation_messages() {
        ConversationStore store = new ConversationStore(temporaryDirectory.resolve("conversation.json"));
        List<ConversationMessage> messages = List.of(
                ConversationMessage.user("안녕"),
                ConversationMessage.assistant("안녕하세요.")
        );

        Optional<String> saveWarning = store.save(messages);
        ConversationStore.LoadResult loaded = store.load();

        assertTrue(saveWarning.isEmpty());
        assertTrue(loaded.warning().isEmpty());
        assertEquals(messages, loaded.messages());
    }

    @Test
    void malformed_json_returns_empty_history_with_warning() throws Exception {
        Path historyFile = temporaryDirectory.resolve("conversation.json");
        Files.writeString(historyFile, "{not-json}");

        ConversationStore.LoadResult loaded = new ConversationStore(historyFile).load();

        assertTrue(loaded.messages().isEmpty());
        assertTrue(loaded.warning().isPresent());
    }

    @Test
    void recent_window_keeps_only_latest_ten_pairs() {
        List<ConversationMessage> messages = new ArrayList<>();
        IntStream.rangeClosed(1, 11).forEach(index -> {
            messages.add(ConversationMessage.user("user-" + index));
            messages.add(ConversationMessage.assistant("assistant-" + index));
        });

        List<ConversationMessage> recent = ConversationWindow.latestPairs(messages, 10);

        assertEquals(20, recent.size());
        assertEquals("user-2", recent.getFirst().content());
        assertEquals("assistant-11", recent.getLast().content());
    }
}
