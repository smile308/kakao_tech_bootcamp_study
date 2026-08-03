package kr.adapterz.springdatajpa.service;

import jakarta.persistence.EntityManager;
import kr.adapterz.springdatajpa.entity.Post;
import kr.adapterz.springdatajpa.entity.PostCounter;
import kr.adapterz.springdatajpa.entity.User;
import kr.adapterz.springdatajpa.repository.CommentRepository;
import kr.adapterz.springdatajpa.repository.LikeRepository;
import kr.adapterz.springdatajpa.repository.PostCounterRepository;
import kr.adapterz.springdatajpa.repository.PostReportRepository;
import kr.adapterz.springdatajpa.repository.PostRepository;
import kr.adapterz.springdatajpa.repository.PostViewCountRepository;
import kr.adapterz.springdatajpa.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@ActiveProfiles("test")
@SpringBootTest
class PostConcurrencyIntegrationTest {

    @Autowired
    private PostService postService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PostRepository postRepository;

    @Autowired
    private PostCounterRepository postCounterRepository;

    @Autowired
    private PostViewCountRepository postViewCountRepository;

    @Autowired
    private LikeRepository likeRepository;

    @Autowired
    private CommentRepository commentRepository;

    @Autowired
    private PostReportRepository postReportRepository;

    @Autowired
    private EntityManager entityManager;

    @BeforeEach
    void setUp() {
        cleanUp();
    }

    @AfterEach
    void tearDown() {
        cleanUp();
    }

    @Test
    void 동시에_게시글을_조회해도_조회수가_요청_수만큼_증가한다() throws Exception {
        int requestCount = 30;
        User writer = userRepository.saveAndFlush(
                createUser("view-writer@test.com", "조회작성자")
        );
        User viewer = userRepository.saveAndFlush(
                createUser("view-viewer@test.com", "조회자")
        );
        Post post = postRepository.saveAndFlush(
                new Post(writer, "조회수 테스트", "조회수 본문")
        );
        Long postId = post.getPostId();
        Long viewerId = viewer.getUserId();
        entityManager.clear();

        runConcurrently(
                requestCount,
                ignored -> postService.getPostView(postId, viewerId)
        );

        long separatedViewCount = postViewCountRepository.findById(postId)
                .orElseThrow()
                .getViewCount();
        int legacyViewCount = postCounterRepository.findById(postId)
                .orElseThrow()
                .getViewCount();

        assertThat(separatedViewCount).isEqualTo(requestCount);
        assertThat(legacyViewCount).isZero();
    }

    @Test
    void 서로_다른_유저가_동시에_좋아요를_눌러도_좋아요_수가_유실되지_않는다() throws Exception {
        int requestCount = 10;
        User writer = userRepository.saveAndFlush(
                createUser("like-writer@test.com", "좋아요작성자")
        );
        Post post = postRepository.saveAndFlush(
                new Post(writer, "좋아요 테스트", "좋아요 본문")
        );
        List<User> users = saveUsers("like-user", requestCount);
        Long postId = post.getPostId();
        entityManager.clear();

        runConcurrently(
                requestCount,
                index -> postService.likePost(postId, users.get(index).getUserId())
        );

        PostCounter savedCounter = postCounterRepository.findById(postId).orElseThrow();

        assertThat(savedCounter.getLikeCount()).isEqualTo(requestCount);
        assertThat(likeRepository.count()).isEqualTo(requestCount);
    }

    @Test
    void 같은_작성자의_여러_게시글이_동시에_신고되어도_작성자의_누적_신고_수가_유실되지_않는다() throws Exception {
        int requestCount = 5;
        User writer = userRepository.saveAndFlush(
                createUser("report-writer@test.com", "신고작성자")
        );
        List<Post> posts = savePosts(writer, requestCount);
        List<User> reporters = saveUsers("report-user", requestCount);
        Long writerId = writer.getUserId();
        entityManager.clear();

        runConcurrently(
                requestCount,
                index -> postService.reportPost(
                        posts.get(index).getPostId(),
                        reporters.get(index).getUserId()
                )
        );

        User savedWriter = userRepository.findById(writerId).orElseThrow();
        List<PostCounter> savedCounters = postCounterRepository.findAllById(
                posts.stream()
                        .map(Post::getPostId)
                        .toList()
        );

        assertThat(savedWriter.getReceivedReportCount()).isEqualTo(requestCount);
        assertThat(savedCounters)
                .extracting(PostCounter::getReportCount)
                .containsOnly(1);
        assertThat(postReportRepository.count()).isEqualTo(requestCount);
    }

    private List<User> saveUsers(String emailPrefix, int count) {
        List<User> users = new ArrayList<>();

        for (int i = 0; i < count; i++) {
            users.add(
                    createUser(
                            emailPrefix + i + "@test.com",
                            "유저" + i
                    )
            );
        }

        return userRepository.saveAllAndFlush(users);
    }

    private List<Post> savePosts(User writer, int count) {
        List<Post> posts = new ArrayList<>();

        for (int i = 0; i < count; i++) {
            posts.add(new Post(writer, "신고 테스트 " + i, "신고 본문"));
        }

        return postRepository.saveAllAndFlush(posts);
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
            int taskCount,
            ConcurrentTask concurrentTask
    ) throws Exception {
        ExecutorService executorService = Executors.newFixedThreadPool(taskCount);
        CountDownLatch readyLatch = new CountDownLatch(taskCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        List<Future<?>> futures = new ArrayList<>();

        try {
            for (int i = 0; i < taskCount; i++) {
                int index = i;
                futures.add(
                        executorService.submit(() -> {
                            readyLatch.countDown();

                            if (!startLatch.await(5, TimeUnit.SECONDS)) {
                                throw new IllegalStateException("Concurrent test start timeout");
                            }

                            concurrentTask.run(index);
                            return null;
                        })
                );
            }

            if (!readyLatch.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Concurrent test ready timeout");
            }

            startLatch.countDown();

            for (Future<?> future : futures) {
                future.get(10, TimeUnit.SECONDS);
            }
        } finally {
            executorService.shutdownNow();
        }
    }

    private void cleanUp() {
        commentRepository.deleteAll();
        postReportRepository.deleteAll();
        likeRepository.deleteAll();
        postRepository.deleteAll();
        userRepository.deleteAll();
    }

    @FunctionalInterface
    private interface ConcurrentTask {
        void run(int index) throws Exception;
    }
}
