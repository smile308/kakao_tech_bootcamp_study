package kr.adapterz.springdatajpa.service;

import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.OptionalLong;
import java.util.Set;

import kr.adapterz.springdatajpa.config.ViewCountProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;

@ExtendWith(MockitoExtension.class)
class RedisViewCountFlushSchedulerTest {

    @Mock
    private RedisViewCountStore redisViewCountStore;

    @Mock
    private ViewCountPersistenceService persistenceService;

    @Mock
    private RedissonClient redissonClient;

    @Mock
    private RLock lock;

    private RedisViewCountFlushScheduler scheduler;

    @BeforeEach
    void setUp() {
        ViewCountProperties properties = new ViewCountProperties(
                true,
                "bamboo:{post-view}:count:",
                "bamboo:{post-view}:dirty",
                "bamboo:{post-view}:flush-lock",
                Duration.ofSeconds(5),
                100
        );
        scheduler = new RedisViewCountFlushScheduler(
                redisViewCountStore,
                persistenceService,
                redissonClient,
                properties
        );
    }

    @Test
    void Redis_스냅샷을_MySQL에_저장한_뒤_dirty_표시를_제거한다() {
        arrangeLockAcquired();
        when(redisViewCountStore.findDirtyPostIds(100))
                .thenReturn(Set.of(42L));
        when(redisViewCountStore.findViewCountSnapshot(42L))
                .thenReturn(OptionalLong.of(150L));

        scheduler.flushDirtyViewCounts();

        InOrder commitBeforeAcknowledge = inOrder(
                lock,
                persistenceService,
                redisViewCountStore
        );
        commitBeforeAcknowledge.verify(lock).tryLock();
        commitBeforeAcknowledge.verify(persistenceService)
                .persist(42L, 150L);
        commitBeforeAcknowledge.verify(redisViewCountStore)
                .acknowledgeIfUnchanged(42L, 150L);
        commitBeforeAcknowledge.verify(lock).unlock();
    }

    @Test
    void MySQL_저장에_실패하면_dirty_표시를_제거하지_않는다() {
        arrangeLockAcquired();
        when(redisViewCountStore.findDirtyPostIds(100))
                .thenReturn(Set.of(42L));
        when(redisViewCountStore.findViewCountSnapshot(42L))
                .thenReturn(OptionalLong.of(150L));
        org.mockito.Mockito.doThrow(new RuntimeException("DB failure"))
                .when(persistenceService)
                .persist(42L, 150L);

        scheduler.flushDirtyViewCounts();

        verify(redisViewCountStore, never())
                .acknowledgeIfUnchanged(42L, 150L);
        verify(lock).unlock();
    }

    @Test
    void Redis_조회수_키가_없으면_MySQL에_반영하지_않는다() {
        arrangeLockAcquired();
        when(redisViewCountStore.findDirtyPostIds(100))
                .thenReturn(Set.of(42L));
        when(redisViewCountStore.findViewCountSnapshot(42L))
                .thenReturn(OptionalLong.empty());

        scheduler.flushDirtyViewCounts();

        verifyNoInteractions(persistenceService);
        verify(redisViewCountStore)
                .removeDirtyIfCountMissing(42L);
        verify(lock).unlock();
    }

    @Test
    void 분산_락을_얻지_못하면_반영_작업을_건너뛴다() {
        when(redissonClient.getLock(
                "bamboo:{post-view}:flush-lock"
        )).thenReturn(lock);
        when(lock.tryLock()).thenReturn(false);

        scheduler.flushDirtyViewCounts();

        verifyNoInteractions(
                redisViewCountStore,
                persistenceService
        );
        verify(lock, never()).unlock();
    }

    private void arrangeLockAcquired() {
        when(redissonClient.getLock(
                "bamboo:{post-view}:flush-lock"
        )).thenReturn(lock);
        when(lock.tryLock()).thenReturn(true);
        when(lock.isHeldByCurrentThread()).thenReturn(true);
    }
}
