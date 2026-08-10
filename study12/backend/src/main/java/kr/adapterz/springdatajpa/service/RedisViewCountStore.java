package kr.adapterz.springdatajpa.service;

import java.util.List;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.OptionalLong;
import java.util.Set;

import kr.adapterz.springdatajpa.config.ViewCountProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(
        prefix = "app.view-count",
        name = "enabled",
        havingValue = "true"
)
public class RedisViewCountStore implements ViewCountUpdater {

    private static final RedisScript<Long> INCREMENT_AND_MARK_DIRTY_SCRIPT =
            new DefaultRedisScript<>("""
                    local count_type = redis.call('TYPE', KEYS[1]).ok
                    local dirty_type = redis.call('TYPE', KEYS[2]).ok

                    if count_type ~= 'none' and count_type ~= 'string' then
                        return redis.error_reply('view count key must be a string')
                    end

                    if dirty_type ~= 'none' and dirty_type ~= 'set' then
                        return redis.error_reply('dirty key must be a set')
                    end

                    local current = redis.call('GET', KEYS[1])
                    local baseline = tonumber(ARGV[1])

                    if not current or tonumber(current) < baseline then
                        redis.call('SET', KEYS[1], ARGV[1])
                    end

                    local updated = redis.call('INCR', KEYS[1])
                    redis.call('SADD', KEYS[2], ARGV[2])

                    return updated
                    """, Long.class);

    private static final RedisScript<Long> ACKNOWLEDGE_IF_UNCHANGED_SCRIPT =
            new DefaultRedisScript<>("""
                    local current = redis.call('GET', KEYS[1])

                    if current and current == ARGV[1] then
                        return redis.call('SREM', KEYS[2], ARGV[2])
                    end

                    return 0
                    """, Long.class);

    private static final RedisScript<Long> REMOVE_DIRTY_IF_COUNT_MISSING_SCRIPT =
            new DefaultRedisScript<>("""
                    if redis.call('EXISTS', KEYS[1]) == 0 then
                        return redis.call('SREM', KEYS[2], ARGV[1])
                    end

                    return 0
                    """, Long.class);

    private final StringRedisTemplate redisTemplate;
    private final ViewCountProperties properties;

    @Override
    public long increment(Long postId, long baselineViewCount) {
        if (baselineViewCount < 0) {
            throw new IllegalArgumentException(
                    "baselineViewCount must not be negative"
            );
        }

        try {
            Long updatedViewCount = redisTemplate.execute(
                    INCREMENT_AND_MARK_DIRTY_SCRIPT,
                    List.of(
                            properties.countKey(postId),
                            properties.dirtySetKey()
                    ),
                    Long.toString(baselineViewCount),
                    Long.toString(postId)
            );

            if (updatedViewCount == null) {
                log.warn(
                        "Redis returned no view count. "
                                + "Serving the last persisted count. postId={}",
                        postId
                );
                return baselineViewCount;
            }

            return updatedViewCount;
        } catch (DataAccessException exception) {
            log.warn(
                    "Redis view count update failed. "
                            + "Serving the last persisted count. postId={}, cause={}",
                    postId,
                    exception.getClass().getSimpleName()
            );
            return baselineViewCount;
        }
    }

    public Set<Long> findDirtyPostIds(int limit) {
        if (limit < 1) {
            throw new IllegalArgumentException("limit must be positive");
        }

        Set<Long> dirtyPostIds = new LinkedHashSet<>();
        ScanOptions options = ScanOptions.scanOptions()
                .count(limit)
                .build();

        try (Cursor<String> cursor = redisTemplate.opsForSet()
                .scan(properties.dirtySetKey(), options)) {
            while (cursor.hasNext() && dirtyPostIds.size() < limit) {
                dirtyPostIds.add(Long.valueOf(cursor.next()));
            }
        }

        return Collections.unmodifiableSet(dirtyPostIds);
    }

    public OptionalLong findViewCountSnapshot(Long postId) {
        String value = redisTemplate.opsForValue()
                .get(properties.countKey(postId));

        if (value == null) {
            return OptionalLong.empty();
        }

        return OptionalLong.of(Long.parseLong(value));
    }

    public boolean removeDirtyIfCountMissing(Long postId) {
        Long removed = redisTemplate.execute(
                REMOVE_DIRTY_IF_COUNT_MISSING_SCRIPT,
                List.of(
                        properties.countKey(postId),
                        properties.dirtySetKey()
                ),
                Long.toString(postId)
        );

        return Long.valueOf(1L).equals(removed);
    }

    public boolean acknowledgeIfUnchanged(
            Long postId,
            long snapshotViewCount
    ) {
        Long removed = redisTemplate.execute(
                ACKNOWLEDGE_IF_UNCHANGED_SCRIPT,
                List.of(
                        properties.countKey(postId),
                        properties.dirtySetKey()
                ),
                Long.toString(snapshotViewCount),
                Long.toString(postId)
        );

        return Long.valueOf(1L).equals(removed);
    }
}
