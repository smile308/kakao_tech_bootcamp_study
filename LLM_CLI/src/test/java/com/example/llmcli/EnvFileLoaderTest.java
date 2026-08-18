// 환경변수와 .env.local에서 API 키를 찾는 우선순위를 검증한다.
package com.example.llmcli;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

class EnvFileLoaderTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void environment_variable_has_priority_over_env_file() throws Exception {
        Path envFile = temporaryDirectory.resolve(".env.local");
        Files.writeString(envFile, "OPENROUTER_API_KEY=file-key\n");

        Optional<String> key = EnvFileLoader.resolve(
                Map.of("OPENROUTER_API_KEY", "environment-key"),
                envFile
        );

        assertEquals(Optional.of("environment-key"), key);
    }

    @Test
    void reads_key_from_env_file_when_environment_variable_is_absent() throws Exception {
        Path envFile = temporaryDirectory.resolve(".env.local");
        Files.writeString(envFile, "# local key\nOPENROUTER_API_KEY=\"file-key\"\n");

        Optional<String> key = EnvFileLoader.resolve(Map.of(), envFile);

        assertEquals(Optional.of("file-key"), key);
    }

    @Test
    void returns_empty_when_key_is_missing() {
        Optional<String> key = EnvFileLoader.resolve(Map.of(), temporaryDirectory.resolve(".env.local"));

        assertEquals(Optional.empty(), key);
    }
}
