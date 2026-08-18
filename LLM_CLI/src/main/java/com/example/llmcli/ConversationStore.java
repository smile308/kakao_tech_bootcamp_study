// 대화 이력을 JSON 파일에 저장하고 다시 읽는다.
package com.example.llmcli;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Optional;

public final class ConversationStore {

    private final Path historyFile;
    private final ObjectMapper objectMapper;

    public ConversationStore(Path historyFile) {
        this.historyFile = historyFile;
        this.objectMapper = new ObjectMapper();
    }

    public LoadResult load() {
        if (!Files.exists(historyFile)) {
            return new LoadResult(List.of(), Optional.empty());
        }

        try {
            List<ConversationMessage> messages = objectMapper.readValue(
                    historyFile.toFile(),
                    new TypeReference<>() {
                    }
            );
            return new LoadResult(List.copyOf(messages), Optional.empty());
        } catch (IOException | RuntimeException exception) {
            return new LoadResult(
                    List.of(),
                    Optional.of("대화 이력을 읽지 못했습니다. 빈 기억으로 시작합니다.")
            );
        }
    }

    public Optional<String> save(List<ConversationMessage> messages) {
        Path absoluteFile = historyFile.toAbsolutePath();
        Path parent = absoluteFile.getParent();
        Path temporaryFile = null;

        try {
            Files.createDirectories(parent);
            temporaryFile = Files.createTempFile(parent, "conversation", ".tmp");
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(temporaryFile.toFile(), messages);
            moveIntoPlace(temporaryFile, absoluteFile);
            return Optional.empty();
        } catch (IOException | RuntimeException exception) {
            return Optional.of("대화 이력을 저장하지 못했습니다. 이번 답변은 계속 표시합니다.");
        } finally {
            if (temporaryFile != null) {
                try {
                    Files.deleteIfExists(temporaryFile);
                } catch (IOException ignored) {
                    // 저장 실패 뒤의 임시 파일 정리 실패는 다음 실행에 영향을 주지 않는다.
                }
            }
        }
    }

    private void moveIntoPlace(Path temporaryFile, Path destination) throws IOException {
        try {
            Files.move(
                    temporaryFile,
                    destination,
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING
            );
        } catch (AtomicMoveNotSupportedException exception) {
            Files.move(temporaryFile, destination, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    public record LoadResult(List<ConversationMessage> messages, Optional<String> warning) {
    }
}
