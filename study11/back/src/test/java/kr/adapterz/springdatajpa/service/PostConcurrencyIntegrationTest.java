package kr.adapterz.springdatajpa.service;

import jakarta.persistence.EntityManager;
import kr.adapterz.springdatajpa.entity.Post;
import kr.adapterz.springdatajpa.entity.User;
import kr.adapterz.springdatajpa.repository.CommentRepository;
import kr.adapterz.springdatajpa.repository.LikeRepository;
import kr.adapterz.springdatajpa.repository.PostReportRepository;
import kr.adapterz.springdatajpa.repository.PostRepository;
import kr.adapterz.springdatajpa.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class PostConcurrencyIntegrationTest {

    @Autowired
    private PostService postService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PostRepository postRepository;

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
    @DisplayName("동시에 게시글을 조회해도 조회수가 요청 수만큼 증가한다")
    void getPostViewIncrementsViewCountAtomically() throws Exception {
        // given
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

        // when
        runConcurrently(
                requestCount,
                ignored -> postService.getPostView(postId, viewerId)
        );

        // then
        Post savedPost = postRepository.findById(postId).orElseThrow();

        assertThat(savedPost.getViewCount()).isEqualTo(requestCount);
    }

    @Test
    @DisplayName("서로 다른 유저가 동시에 좋아요를 눌러도 좋아요 수가 유실되지 않는다")
    void likePostUsesPessimisticLock() throws Exception {
        // given
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

        // when
        runConcurrently(
                requestCount,
                index -> postService.likePost(postId, users.get(index).getUserId())
        );

        // then
        Post savedPost = postRepository.findById(postId).orElseThrow();

        assertThat(savedPost.getLikeCount()).isEqualTo(requestCount);
        assertThat(likeRepository.count()).isEqualTo(requestCount);
    }

    @Test
    @DisplayName("같은 작성자의 여러 게시글이 동시에 신고되어도 작성자의 누적 신고 수가 유실되지 않는다")
    void reportPostLocksWriterUser() throws Exception {
        // given
        int requestCount = 5;
        User writer = userRepository.saveAndFlush(
                createUser("report-writer@test.com", "신고작성자")
        );
        List<Post> posts = savePosts(writer, requestCount);
        List<User> reporters = saveUsers("report-user", requestCount);
        Long writerId = writer.getUserId();
        entityManager.clear();

        // when
        runConcurrently(
                requestCount,
                index -> postService.reportPost(
                        posts.get(index).getPostId(),
                        reporters.get(index).getUserId()
                )
        );

        // then
        User savedWriter = userRepository.findById(writerId).orElseThrow();
        List<Post> savedPosts = postRepository.findAllById(
                posts.stream()
                        .map(Post::getPostId)
                        .toList()
        );

        assertThat(savedWriter.getReceivedReportCount()).isEqualTo(requestCount);
        assertThat(savedPosts)
                .extracting(Post::getReportCount)
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
