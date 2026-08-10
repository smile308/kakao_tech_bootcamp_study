package kr.adapterz.springdatajpa.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.view-count")
public record ViewCountProperties(
        boolean enabled,
        String countKeyPrefix,
        String dirtySetKey,
        String flushLockKey,
        Duration flushInterval,
        int maxPostsPerFlush
) {

    public ViewCountProperties {
        requireText(countKeyPrefix, "countKeyPrefix");
        requireText(dirtySetKey, "dirtySetKey");
        requireText(flushLockKey, "flushLockKey");

        if (flushInterval == null || flushInterval.isZero() || flushInterval.isNegative()) {
            throw new IllegalArgumentException("flushInterval must be positive");
        }

        if (maxPostsPerFlush < 1) {
            throw new IllegalArgumentException("maxPostsPerFlush must be positive");
        }
    }

    public String countKey(Long postId) {
        if (postId == null || postId <= 0) {
            throw new IllegalArgumentException("postId must be positive");
        }
        return countKeyPrefix + postId;
    }

    private static void requireText(String value, String propertyName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(propertyName + " must not be blank");
        }
    }
}
