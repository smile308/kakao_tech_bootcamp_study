// 환경변수 또는 .env.local에서 API 키를 읽는다.
package com.example.llmcli;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class EnvFileLoader {

    private static final String API_KEY_NAME = "OPENROUTER_API_KEY";

    private EnvFileLoader() {
    }

    public static Optional<String> loadApiKey(Path envFile) {
        return resolve(System.getenv(), envFile);
    }

    public static Optional<String> resolve(Map<String, String> environment, Path envFile) {
        String environmentValue = environment.get(API_KEY_NAME);
        if (environmentValue != null && !environmentValue.isBlank()) {
            return Optional.of(environmentValue.trim());
        }

        return readEnvFile(envFile);
    }

    private static Optional<String> readEnvFile(Path envFile) {
        if (!Files.isRegularFile(envFile)) {
            return Optional.empty();
        }

        try {
            List<String> lines = Files.readAllLines(envFile, StandardCharsets.UTF_8);
            for (String line : lines) {
                String trimmedLine = line.trim();
                if (trimmedLine.isEmpty() || trimmedLine.startsWith("#")) {
                    continue;
                }

                int separator = trimmedLine.indexOf('=');
                if (separator < 0) {
                    continue;
                }

                String name = trimmedLine.substring(0, separator).trim();
                if (!API_KEY_NAME.equals(name)) {
                    continue;
                }

                String value = removeOptionalQuotes(trimmedLine.substring(separator + 1).trim());
                if (!value.isBlank()) {
                    return Optional.of(value);
                }
            }
        } catch (IOException ignored) {
            return Optional.empty();
        }

        return Optional.empty();
    }

    private static String removeOptionalQuotes(String value) {
        if (value.length() >= 2) {
            char first = value.charAt(0);
            char last = value.charAt(value.length() - 1);
            if ((first == '"' && last == '"') || (first == '\'' && last == '\'')) {
                return value.substring(1, value.length() - 1);
            }
        }
        return value;
    }
}
