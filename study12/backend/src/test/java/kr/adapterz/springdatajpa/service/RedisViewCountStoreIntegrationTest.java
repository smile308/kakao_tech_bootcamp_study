package kr.adapterz.springdatajpa.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.OptionalLong;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import kr.adapterz.springdatajpa.config.ViewCountProperties;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.redisson.Redisson;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.redisson.spring.data.connection.RedissonConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@Tag("redis-integration")
@Testcontainers
class RedisViewCountStoreIntegrationTest {

    private static final int REDIS_PORT = 6379;
    private static final String COUNT_KEY =
            "bamboo:{post-view}:count:42";
    private static final String DIRTY_SET_KEY =
            "bamboo:{post-view}:dirty";
    private static final String FLUSH_LOCK_KEY =
            "bamboo:{post-view}:flush-lock";

    @Container
    private static final GenericContainer<?> REDIS =
            new GenericContainer<>(
                    DockerImageName.parse("redis:7.4-alpine")
            )
                    .withExposedPorts(REDIS_PORT)
                    .waitingFor(Wait.forListeningPort());

    private static RedissonClient redissonClient;
    private static RedissonClient secondRedissonClient;
    private static StringRedisTemplate redisTemplate;
    private static RedisViewCountStore store;

    @BeforeAll
    static void connectToRedis() throws Exception {
        Config config = new Config();
        config.useSingleServer()
                .setAddress(
                        "redis://"
                                + REDIS.getHost()
                                + ":"
                                + REDIS.getMappedPort(REDIS_PORT)
                );

        redissonClient = Redisson.create(config);
        secondRedissonClient = Redisson.create(config);

        RedissonConnectionFactory connectionFactory =
                new RedissonConnectionFactory(redissonClient);
        connectionFactory.afterPropertiesSet();

        redisTemplate = new StringRedisTemplate(connectionFactory);
        redisTemplate.afterPropertiesSet();

        ViewCountProperties properties = new ViewCountProperties(
                true,
                "bamboo:{post-view}:count:",
                DIRTY_SET_KEY,
                "bamboo:{post-view}:flush-lock",
                Duration.ofSeconds(5),
                100
        );
        store = new RedisViewCountStore(redisTemplate, properties);
    }

    @AfterAll
    static void disconnectFromRedis() {
        if (redissonClient != null) {
            redissonClient.shutdown();
        }
        if (secondRedissonClient != null) {
            secondRedissonClient.shutdown();
        }
    }

    @BeforeEach
    void clearViewCountKeys() {
        redisTemplate.delete(List.of(COUNT_KEY, DIRTY_SET_KEY));
    }

    @Test
    void Redis_키가_없으면_DB_기준값에서_조회수를_증가시킨다() {
        long updated = store.increment(42L, 100L);

        assertThat(updated).isEqualTo(101L);
        assertThat(redisTemplate.opsForValue().get(COUNT_KEY))
                .isEqualTo("101");
        assertThat(redisTemplate.opsForSet().isMember(
                DIRTY_SET_KEY,
                "42"
        )).isTrue();
    }

    @Test
    void Redis와_DB_중_더_큰_조회수를_기준으로_증가시킨다() {
        redisTemplate.opsForValue().set(COUNT_KEY, "150");

        long updated = store.increment(42L, 100L);

        assertThat(updated).isEqualTo(151L);
        assertThat(redisTemplate.opsForValue().get(COUNT_KEY))
                .isEqualTo("151");
    }

    @Test
    void Redis_조회수가_DB보다_작으면_DB_값으로_복구한_뒤_증가시킨다() {
        redisTemplate.opsForValue().set(COUNT_KEY, "90");

        long updated = store.increment(42L, 100L);

        assertThat(updated).isEqualTo(101L);
        assertThat(redisTemplate.opsForValue().get(COUNT_KEY))
                .isEqualTo("101");
    }

    @Test
    void 동시에_200번_증가해도_조회수가_유실되지_않는다()
            throws Exception {
        int requestCount = 200;
        CountDownLatch startSignal = new CountDownLatch(1);
        List<Future<Long>> results = new ArrayList<>();

        try (ExecutorService executor =
                     Executors.newFixedThreadPool(20)) {
            for (int index = 0; index < requestCount; index++) {
                results.add(executor.submit(() -> {
                    startSignal.await();
                    return store.increment(42L, 100L);
                }));
            }

            startSignal.countDown();

            for (Future<Long> result : results) {
                assertThat(result.get(10, TimeUnit.SECONDS))
                        .isBetween(101L, 300L);
            }
        }

        assertThat(redisTemplate.opsForValue().get(COUNT_KEY))
                .isEqualTo("300");
        assertThat(redisTemplate.opsForSet().size(DIRTY_SET_KEY))
                .isEqualTo(1L);
        assertThat(redisTemplate.opsForSet().isMember(
                DIRTY_SET_KEY,
                "42"
        )).isTrue();
    }

    @Test
    void 저장한_스냅샷보다_조회수가_증가하면_dirty_표시를_유지한다() {
        long firstSnapshot = store.increment(42L, 100L);

        assertThat(store.findDirtyPostIds(100)).containsExactly(42L);
        assertThat(store.findViewCountSnapshot(42L))
                .isEqualTo(OptionalLong.of(101L));

        store.increment(42L, 100L);

        boolean staleSnapshotAcknowledged =
                store.acknowledgeIfUnchanged(42L, firstSnapshot);

        assertThat(staleSnapshotAcknowledged).isFalse();
        assertThat(store.findDirtyPostIds(100)).containsExactly(42L);

        boolean latestSnapshotAcknowledged =
                store.acknowledgeIfUnchanged(42L, 102L);

        assertThat(latestSnapshotAcknowledged).isTrue();
        assertThat(store.findDirtyPostIds(100)).isEmpty();
    }

    @Test
    void 조회수_키가_없을_때만_고아_dirty_표시를_제거한다() {
        redisTemplate.opsForSet().add(DIRTY_SET_KEY, "42");

        assertThat(store.removeDirtyIfCountMissing(42L)).isTrue();
        assertThat(store.findDirtyPostIds(100)).isEmpty();

        store.increment(42L, 100L);

        assertThat(store.removeDirtyIfCountMissing(42L)).isFalse();
        assertThat(store.findDirtyPostIds(100)).containsExactly(42L);
        assertThat(redisTemplate.opsForValue().get(COUNT_KEY))
                .isEqualTo("101");
    }

    @Test
    void 두_백엔드_중_한_백엔드만_분산_락을_획득한다() {
        RLock firstBackendLock =
                redissonClient.getLock(FLUSH_LOCK_KEY);
        RLock secondBackendLock =
                secondRedissonClient.getLock(FLUSH_LOCK_KEY);

        assertThat(firstBackendLock.tryLock()).isTrue();

        try {
            assertThat(secondBackendLock.tryLock()).isFalse();
        } finally {
            firstBackendLock.unlock();
        }

        assertThat(secondBackendLock.tryLock()).isTrue();
        secondBackendLock.unlock();
    }
}
