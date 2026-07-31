package kr.adapterz.springdatajpa.service;

import java.util.OptionalLong;

import kr.adapterz.springdatajpa.config.ViewCountProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.redisson.client.RedisException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DataAccessException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(
        prefix = "app.view-count",
        name = "enabled",
        havingValue = "true"
)
public class RedisViewCountFlushScheduler {

    private final RedisViewCountStore redisViewCountStore;
    private final ViewCountPersistenceService persistenceService;
    private final RedissonClient redissonClient;
    private final ViewCountProperties properties;

    @Scheduled(
            initialDelayString = "${app.view-count.flush-interval}",
            fixedDelayString = "${app.view-count.flush-interval}"
    )
    public void flushDirtyViewCounts() {
        RLock lock = redissonClient.getLock(properties.flushLockKey());
        boolean acquired = false;

        try {
            acquired = lock.tryLock();

            if (!acquired) {
                return;
            }

            flushWhileHoldingLock();
        } catch (RedisException exception) {
            log.warn(
                    "Redis view count flush lock failed. cause={}",
                    exception.getClass().getSimpleName()
            );
        } finally {
            releaseIfOwned(lock, acquired);
        }
    }

    private void flushWhileHoldingLock() {
        try {
            for (Long postId : redisViewCountStore.findDirtyPostIds()) {
                flushOne(postId);
            }
        } catch (DataAccessException exception) {
            log.warn(
                    "Redis view count flush was skipped. cause={}",
                    exception.getClass().getSimpleName()
            );
        }
    }

    private void flushOne(Long postId) {
        try {
            OptionalLong snapshot =
                    redisViewCountStore.findViewCountSnapshot(postId);

            if (snapshot.isEmpty()) {
                return;
            }

            long snapshotViewCount = snapshot.getAsLong();

            persistenceService.persist(postId, snapshotViewCount);

            redisViewCountStore.acknowledgeIfUnchanged(
                    postId,
                    snapshotViewCount
            );
        } catch (RuntimeException exception) {
            log.warn(
                    "Failed to persist Redis view count. "
                            + "It will be retried. postId={}, cause={}",
                    postId,
                    exception.getClass().getSimpleName()
            );
        }
    }

    private void releaseIfOwned(RLock lock, boolean acquired) {
        if (!acquired) {
            return;
        }

        try {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        } catch (RedisException exception) {
            log.warn(
                    "Redis view count flush lock release failed. cause={}",
                    exception.getClass().getSimpleName()
            );
        }
    }
}
