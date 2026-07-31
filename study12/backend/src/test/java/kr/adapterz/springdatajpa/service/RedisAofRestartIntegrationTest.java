package kr.adapterz.springdatajpa.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;

import kr.adapterz.springdatajpa.config.ViewCountProperties;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.redisson.spring.data.connection.RedissonConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@Tag("redis-integration")
@Testcontainers
class RedisAofRestartIntegrationTest {

    private static final int REDIS_PORT = 6379;
    private static final String COUNT_KEY =
            "bamboo:{post-view}:count:42";
    private static final String DIRTY_SET_KEY =
            "bamboo:{post-view}:dirty";

    @Container
    private static final GenericContainer<?> REDIS =
            new GenericContainer<>(
                    DockerImageName.parse("redis:7.4-alpine")
            )
                    .withCommand(
                            "redis-server",
                            "--appendonly",
                            "yes",
                            "--appendfsync",
                            "everysec"
                    )
                    .withExposedPorts(REDIS_PORT)
                    .waitingFor(Wait.forListeningPort());

    private static RedissonClient redissonClient;
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
                Duration.ofSeconds(5)
        );
        store = new RedisViewCountStore(redisTemplate, properties);
    }

    @AfterAll
    static void disconnectFromRedis() {
        if (redissonClient != null) {
            redissonClient.shutdown();
        }
    }

    @Test
    void Redis_프로세스가_재시작되어도_AOF_조회수가_유지된다()
            throws Exception {
        assertThat(store.increment(42L, 100L)).isEqualTo(101L);

        Thread.sleep(1_500L);

        DockerClientFactory.instance()
                .client()
                .restartContainerCmd(REDIS.getContainerId())
                .withTimeout(10)
                .exec();

        waitForAofDataRecovery();

        assertThat(REDIS.execInContainer(
                "redis-cli",
                "--raw",
                "GET",
                COUNT_KEY
        ).getStdout().trim())
                .isEqualTo("101");
        assertThat(REDIS.execInContainer(
                "redis-cli",
                "--raw",
                "SISMEMBER",
                DIRTY_SET_KEY,
                "42"
        ).getStdout().trim()).isEqualTo("1");
    }

    private void waitForAofDataRecovery() throws InterruptedException {
        long deadline = System.nanoTime()
                + Duration.ofSeconds(30).toNanos();
        Exception lastFailure = null;

        while (System.nanoTime() < deadline) {
            try {
                String count = REDIS.execInContainer(
                        "redis-cli",
                        "--raw",
                        "GET",
                        COUNT_KEY
                ).getStdout().trim();
                String dirty = REDIS.execInContainer(
                        "redis-cli",
                        "--raw",
                        "SISMEMBER",
                        DIRTY_SET_KEY,
                        "42"
                ).getStdout().trim();

                if ("101".equals(count) && "1".equals(dirty)) {
                    return;
                }
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw exception;
            } catch (Exception exception) {
                lastFailure = exception;
            }

            Thread.sleep(100L);
        }

        throw new AssertionError(
                "Redis AOF data did not recover within 30 seconds",
                lastFailure
        );
    }
}
