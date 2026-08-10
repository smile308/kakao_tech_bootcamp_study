package kr.adapterz.springdatajpa.config;

import jakarta.persistence.EntityManager;
import kr.adapterz.springdatajpa.dto.comment.CommentPostRequestDto;
import kr.adapterz.springdatajpa.entity.Post;
import kr.adapterz.springdatajpa.entity.PostCounter;
import kr.adapterz.springdatajpa.entity.User;
import kr.adapterz.springdatajpa.repository.PostCounterRepository;
import kr.adapterz.springdatajpa.repository.PostRepository;
import kr.adapterz.springdatajpa.repository.PostViewCountRepository;
import kr.adapterz.springdatajpa.repository.UserRepository;
import kr.adapterz.springdatajpa.service.CommentService;
import kr.adapterz.springdatajpa.service.PostService;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.util.ReflectionTestUtils;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
    private PostService postService;

    @Autowired
    private PostViewCountRepository postViewCountRepository;

    @Autowired
    private CommentService commentService;

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

        assertThat(currentTableCount).isEqualTo(9);
        assertThat(baselineHistoryCount).isEqualTo(1);
        Integer legacyViewColumnCount = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM information_schema.columns
                WHERE table_schema = DATABASE()
                  AND table_name = 'post_counters'
                  AND column_name = 'view_count'
                """, Integer.class);
        assertThat(legacyViewColumnCount).isZero();
        Integer postFixedColumnCount = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM information_schema.columns
                WHERE table_schema = DATABASE()
                  AND table_name = 'posts'
                  AND column_name = 'is_fixed'
                """, Integer.class);
        assertThat(postFixedColumnCount).isZero();

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
        assertThat(countLikesForPost(postId)).isEqualTo(userIds.size());
    }

    @Test
    void MySQL에서_게시글을_동시에_조회해도_조회수가_요청_수만큼_증가한다()
            throws Exception {
        int requestCount = 30;
        User writer = userRepository.saveAndFlush(
                createUser("mysql-view-writer@test.com", "MySQL조회작성자")
        );
        User viewer = userRepository.saveAndFlush(
                createUser("mysql-view-viewer@test.com", "MySQL조회자")
        );
        Post post = postRepository.saveAndFlush(
                new Post(writer, "MySQL 조회수 테스트", "MySQL 조회수 경합 검증")
        );
        Long postId = post.getPostId();
        Long viewerId = viewer.getUserId();
        List<Long> viewerIds = new ArrayList<>();

        for (int index = 0; index < requestCount; index++) {
            viewerIds.add(viewerId);
        }

        entityManager.clear();

        runConcurrently(
                viewerIds,
                ignored -> postService.getPostView(postId, viewerId)
        );

        long viewCount = postViewCountRepository.findById(postId)
                .orElseThrow()
                .getViewCount();
        assertThat(viewCount).isEqualTo(requestCount);
    }

    @Test
    void MySQL에서_같은_작성자의_여러_게시글이_동시에_신고되어도_누적_수가_유실되지_않는다()
            throws Exception {
        int requestCount = 5;
        User writer = userRepository.saveAndFlush(
                createUser("mysql-report-writer@test.com", "MySQL신고작성자")
        );
        List<Post> posts = new ArrayList<>();

        for (int index = 0; index < requestCount; index++) {
            posts.add(
                    postRepository.save(
                            new Post(
                                    writer,
                                    "MySQL 신고 테스트 " + index,
                                    "MySQL 신고 누적 경합 검증"
                            )
                    )
            );
        }
        postRepository.flush();

        List<User> reporters = new ArrayList<>();
        for (int index = 0; index < requestCount; index++) {
            reporters.add(
                    createUser(
                            "mysql-report-user-" + index + "@test.com",
                            "MySQL신고자" + index
                    )
            );
        }
        reporters = userRepository.saveAllAndFlush(reporters);

        Map<Long, Long> postIdByReporterId = new HashMap<>();
        for (int index = 0; index < requestCount; index++) {
            postIdByReporterId.put(
                    reporters.get(index).getUserId(),
                    posts.get(index).getPostId()
            );
        }
        Long writerId = writer.getUserId();
        List<Long> reporterIds = reporters.stream()
                .map(User::getUserId)
                .toList();
        entityManager.clear();

        runConcurrently(
                reporterIds,
                reporterId -> postService.reportPost(
                        postIdByReporterId.get(reporterId),
                        reporterId
                )
        );

        User savedWriter = userRepository.findById(writerId).orElseThrow();
        assertThat(savedWriter.getReceivedReportCount()).isEqualTo(requestCount);

        for (Post post : posts) {
            Long postId = post.getPostId();
            PostCounter savedCounter =
                    postCounterRepository.findById(postId).orElseThrow();

            assertThat(savedCounter.getReportCount()).isEqualTo(1);
            assertThat(countReportsForPost(postId)).isEqualTo(1);
        }
    }

    @Test
    void 같은_게시글에_신고_좋아요_댓글이_동시에_들어와도_락_순서가_일치한다()
            throws Exception {
        User writer = userRepository.saveAndFlush(
                createUser("mixed-writer@test.com", "혼합작성자")
        );
        Post post = postRepository.saveAndFlush(
                new Post(writer, "혼합 동시성 테스트", "혼합 락 순서 검증 본문")
        );
        User reporter = userRepository.saveAndFlush(
                createUser("mixed-reporter@test.com", "혼합신고자")
        );
        User liker = userRepository.saveAndFlush(
                createUser("mixed-liker@test.com", "혼합좋아요자")
        );
        User commenter = userRepository.saveAndFlush(
                createUser("mixed-commenter@test.com", "혼합댓글자")
        );
        Long postId = post.getPostId();
        Long reporterId = reporter.getUserId();
        Long likerId = liker.getUserId();
        Long commenterId = commenter.getUserId();
        entityManager.clear();

        runConcurrently(
                List.of(reporterId, likerId, commenterId),
                userId -> {
                    if (userId.equals(reporterId)) {
                        postService.reportPost(postId, reporterId);
                    } else if (userId.equals(likerId)) {
                        postService.likePost(postId, likerId);
                    } else {
                        commentService.commentPost(
                                postId,
                                commenterId,
                                createCommentRequest()
                        );
                    }
                }
        );

        PostCounter savedCounter =
                postCounterRepository.findById(postId).orElseThrow();

        assertThat(savedCounter.getReportCount()).isEqualTo(1);
        assertThat(savedCounter.getLikeCount()).isEqualTo(1);
        assertThat(savedCounter.getReplyCount()).isEqualTo(1);
        assertThat(countLikesForPost(postId)).isEqualTo(1);
        assertThat(countCommentsForPost(postId)).isEqualTo(1);
        assertThat(countReportsForPost(postId)).isEqualTo(1);
    }

    private int countLikesForPost(Long postId) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM post_likes WHERE post_id = ?",
                Integer.class,
                postId
        );
    }

    private int countCommentsForPost(Long postId) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM comments WHERE post_id = ?",
                Integer.class,
                postId
        );
    }

    private int countReportsForPost(Long postId) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM post_reports WHERE post_id = ?",
                Integer.class,
                postId
        );
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
            ConcurrentTask concurrentTask
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

                            concurrentTask.run(userId);
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
    private interface ConcurrentTask {
        void run(Long userId);
    }

    private CommentPostRequestDto createCommentRequest() {
        CommentPostRequestDto request = new CommentPostRequestDto();
        ReflectionTestUtils.setField(request, "commentContent", "동시성 댓글");
        return request;
    }
}
