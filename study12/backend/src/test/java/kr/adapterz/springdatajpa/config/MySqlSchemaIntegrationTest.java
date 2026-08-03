package kr.adapterz.springdatajpa.config;

import jakarta.persistence.EntityManager;
import kr.adapterz.springdatajpa.entity.Post;
import kr.adapterz.springdatajpa.entity.PostCounter;
import kr.adapterz.springdatajpa.entity.User;
import kr.adapterz.springdatajpa.repository.LikeRepository;
import kr.adapterz.springdatajpa.repository.PostCounterRepository;
import kr.adapterz.springdatajpa.repository.PostRepository;
import kr.adapterz.springdatajpa.repository.UserRepository;
import kr.adapterz.springdatajpa.service.PostService;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("mysql-integration")
@Testcontainers
@ActiveProfiles("test")
@SpringBootTest(properties = {
        "spring.flyway.enabled=true",
        "spring.flyway.baseline-on-migrate=false",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.jpa.defer-datasource-initialization=false",
        "spring.sql.init.mode=never",
        "app.view-count.enabled=false"
})
class MySqlSchemaIntegrationTest {

    private static final String DATABASE_NAME = "study_test";
    private static final String ROOT_PASSWORD = "test-password";

    @Container
    private static final GenericContainer<?> MYSQL =
            new GenericContainer<>(DockerImageName.parse("mysql:8.4"))
                    .withEnv("MYSQL_DATABASE", DATABASE_NAME)
                    .withEnv("MYSQL_ROOT_PASSWORD", ROOT_PASSWORD)
                    .withExposedPorts(3306)
                    .withStartupTimeout(Duration.ofMinutes(2));

    @DynamicPropertySource
    static void configureMySql(DynamicPropertyRegistry registry) {
        registry.add(
                "spring.datasource.url",
                () -> "jdbc:mysql://%s:%d/%s?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC"
                        .formatted(
                                MYSQL.getHost(),
                                MYSQL.getMappedPort(3306),
                                DATABASE_NAME
                        )
        );
        registry.add("spring.datasource.username", () -> "root");
        registry.add("spring.datasource.password", () -> ROOT_PASSWORD);
        registry.add(
                "spring.datasource.driver-class-name",
                () -> "com.mysql.cj.jdbc.Driver"
        );
    }

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PostRepository postRepository;

    @Autowired
    private PostCounterRepository postCounterRepository;

    @Autowired
    private LikeRepository likeRepository;

    @Autowired
    private PostService postService;

    @Autowired
    private EntityManager entityManager;

    @Test
    void B3로_빈_MySQL을_생성하고_동시_좋아요를_유실하지_않는다()
            throws Exception {
        Integer currentTableCount = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM information_schema.tables
                WHERE table_schema = DATABASE()
                  AND table_name IN (
                      'users',
                      'posts',
                      'auth_sessions',
                      'comments',
                      'post_counters',
                      'post_images',
                      'post_likes',
                      'post_likes_seq',
                      'post_reports',
                      'post_view_counts'
                  )
                """, Integer.class);
        Integer baselineHistoryCount = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM flyway_schema_history
                WHERE script = 'B3__current_schema.sql'
                  AND success = TRUE
                """, Integer.class);

        assertThat(currentTableCount).isEqualTo(10);
        assertThat(baselineHistoryCount).isEqualTo(1);

        User writer = userRepository.saveAndFlush(
                createUser("mysql-writer@test.com", "MySQL작성자")
        );
        Post post = postRepository.saveAndFlush(
                new Post(writer, "MySQL 동시 좋아요", "MySQL 락 검증 본문")
        );
        List<User> users = new ArrayList<>();

        for (int index = 0; index < 5; index++) {
            users.add(
                    createUser(
                            "mysql-user-" + index + "@test.com",
                            "MySQL유저" + index
                    )
            );
        }

        users = userRepository.saveAllAndFlush(users);
        Long postId = post.getPostId();
        List<Long> userIds = users.stream()
                .map(User::getUserId)
                .toList();
        entityManager.clear();

        runConcurrently(
                userIds,
                userId -> postService.likePost(postId, userId)
        );

        PostCounter savedCounter =
                postCounterRepository.findById(postId).orElseThrow();

        assertThat(savedCounter.getLikeCount()).isEqualTo(userIds.size());
        assertThat(likeRepository.count()).isEqualTo(userIds.size());
    }

    private User createUser(String email, String nickname) {
        return new User(
                email,
                "encoded-password",
                nickname,
                "profile.png",
                0
        );
    }

    private void runConcurrently(
            List<Long> userIds,
            ConcurrentLike concurrentLike
    ) throws Exception {
        ExecutorService executorService =
                Executors.newFixedThreadPool(userIds.size());
        CountDownLatch readyLatch = new CountDownLatch(userIds.size());
        CountDownLatch startLatch = new CountDownLatch(1);
        List<Future<?>> futures = new ArrayList<>();

        try {
            for (Long userId : userIds) {
                futures.add(
                        executorService.submit(() -> {
                            readyLatch.countDown();

                            if (!startLatch.await(10, TimeUnit.SECONDS)) {
                                throw new IllegalStateException(
                                        "MySQL concurrency test start timeout"
                                );
                            }

                            concurrentLike.run(userId);
                            return null;
                        })
                );
            }

            if (!readyLatch.await(10, TimeUnit.SECONDS)) {
                throw new IllegalStateException(
                        "MySQL concurrency test ready timeout"
                );
            }

            startLatch.countDown();

            for (Future<?> future : futures) {
                future.get(20, TimeUnit.SECONDS);
            }
        } finally {
            executorService.shutdownNow();
        }
    }

    @FunctionalInterface
    private interface ConcurrentLike {
        void run(Long userId);
    }
}

