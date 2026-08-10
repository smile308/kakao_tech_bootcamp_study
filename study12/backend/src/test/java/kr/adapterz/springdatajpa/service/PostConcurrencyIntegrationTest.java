package kr.adapterz.springdatajpa.service;

import jakarta.persistence.EntityManager;
import kr.adapterz.springdatajpa.dto.post.PostFixRequestDto;
import kr.adapterz.springdatajpa.entity.Post;
import kr.adapterz.springdatajpa.entity.PostCounter;
import kr.adapterz.springdatajpa.entity.User;
import kr.adapterz.springdatajpa.repository.CommentRepository;
import kr.adapterz.springdatajpa.repository.LikeRepository;
import kr.adapterz.springdatajpa.repository.PostCounterRepository;
import kr.adapterz.springdatajpa.repository.PostReportRepository;
import kr.adapterz.springdatajpa.repository.PostRepository;
import kr.adapterz.springdatajpa.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.Collections;

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
    private LikeRepository likeRepository;

    @Autowired
    private CommentRepository commentRepository;

    @Autowired
    private PostReportRepository postReportRepository;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @BeforeEach
    void setUp() {
        cleanUp();
    }

    @AfterEach
    void tearDown() {
        cleanUp();
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
    void 같은_내용의_수정이_동시에_요청되어도_게시글_버전은_증가하지_않는다() throws Exception {
        User writer = userRepository.saveAndFlush(
                createUser("same-update-writer@test.com", "같은내용작성자")
        );
        Post post = postRepository.saveAndFlush(
                new Post(writer, "같은 제목", "같은 내용")
        );
        Long postId = post.getPostId();
        Long writerId = writer.getUserId();
        Long version = post.getVersion();
        PostFixRequestDto request = createPostFixRequest(
                version,
                "같은 제목",
                "같은 내용",
                List.of()
        );
        entityManager.clear();

        runConcurrently(
                2,
                ignored -> postService.fixPost(postId, writerId, request)
        );

        Post savedPost = postRepository.findById(postId).orElseThrow();
        assertThat(savedPost.getVersion()).isEqualTo(version);
        assertThat(savedPost.getPostTitle()).isEqualTo("같은 제목");
        assertThat(savedPost.getPostContent()).isEqualTo("같은 내용");
    }

    @Test
    void 서로_다른_내용의_수정이_동시에_요청되면_한_요청만_성공한다() throws Exception {
        User writer = userRepository.saveAndFlush(
                createUser("different-update-writer@test.com", "동시수정작성자")
        );
        Post post = postRepository.saveAndFlush(
                new Post(writer, "기존 제목", "기존 내용")
        );
        Long postId = post.getPostId();
        Long version = post.getVersion();
        List<Throwable> failures = Collections.synchronizedList(new ArrayList<>());
        int[] successCount = {0};
        CountDownLatch readyLatch = new CountDownLatch(2);
        CountDownLatch startLatch = new CountDownLatch(1);
        ExecutorService executorService = Executors.newFixedThreadPool(2);
        List<Future<?>> futures = new ArrayList<>();

        try {
            for (int index = 0; index < 2; index++) {
                String title = index == 0 ? "첫 번째 제목" : "두 번째 제목";
                String content = index == 0 ? "첫 번째 내용" : "두 번째 내용";
                futures.add(executorService.submit(() -> {
                    try {
                        new TransactionTemplate(transactionManager)
                                .executeWithoutResult(status -> {
                                    Post loadedPost = entityManager.find(Post.class, postId);
                                    readyLatch.countDown();
                                    awaitLatch(startLatch);
                                    loadedPost.update(title, content, List.of());
                                    entityManager.flush();
                                });
                        synchronized (successCount) {
                            successCount[0]++;
                        }
                    } catch (Throwable exception) {
                        failures.add(exception);
                    }
                }));
            }

            if (!readyLatch.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Concurrent update read barrier timeout");
            }
            startLatch.countDown();

            for (Future<?> future : futures) {
                future.get(10, TimeUnit.SECONDS);
            }
        } finally {
            executorService.shutdownNow();
        }

        Post savedPost = postRepository.findById(postId).orElseThrow();
        assertThat(successCount[0]).isEqualTo(1);
        assertThat(failures).hasSize(1);
        assertThat(containsOptimisticLockFailure(failures.get(0))).isTrue();
        assertThat(savedPost.getVersion()).isEqualTo(version + 1);
        assertThat(List.of("첫 번째 제목", "두 번째 제목"))
                .contains(savedPost.getPostTitle());
    }

    private void awaitLatch(CountDownLatch latch) {
        try {
            if (!latch.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Concurrent update start barrier timeout");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Concurrent update was interrupted", exception);
        }
    }

    private boolean containsOptimisticLockFailure(Throwable throwable) {
        Throwable current = throwable;

        while (current != null) {
            if (current.getClass().getName().contains("Optimistic")) {
                return true;
            }
            current = current.getCause();
        }

        return false;
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

    private User createUser(String email, String nickname) {
        return new User(
                email,
                "encoded-password",
                nickname,
                "profile.png",
                0
        );
    }

    private PostFixRequestDto createPostFixRequest(
            Long version,
            String title,
            String content,
            List<String> imageFiles
    ) {
        PostFixRequestDto request = new PostFixRequestDto();
        ReflectionTestUtils.setField(request, "version", version);
        ReflectionTestUtils.setField(request, "title", title);
        ReflectionTestUtils.setField(request, "content", content);
        ReflectionTestUtils.setField(request, "imageFiles", imageFiles);
        return request;
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
