package kr.adapterz.springdatajpa.repository;

import jakarta.persistence.EntityManager;
import kr.adapterz.springdatajpa.entity.Post;
import kr.adapterz.springdatajpa.entity.PostViewCount;
import kr.adapterz.springdatajpa.entity.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional
class PostViewCountRepositoryIntegrationTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PostRepository postRepository;

    @Autowired
    private PostViewCountRepository postViewCountRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    @DisplayName("게시글 저장 시 조회수 카운터도 같은 식별자로 저장된다")
    void postSaveCascadesToPostViewCount() {
        User writer = userRepository.save(
                new User(
                        "view-count-writer@test.com",
                        "encoded-password",
                        "조회수작성자",
                        "profile.png",
                        0
                )
        );
        Post post = postRepository.save(
                new Post(writer, "조회수 카운터 게시글", "본문")
        );

        entityManager.flush();
        entityManager.clear();

        PostViewCount savedViewCount = postViewCountRepository
                .findById(post.getPostId())
                .orElseThrow();

        assertThat(savedViewCount.getPostId()).isEqualTo(post.getPostId());
        assertThat(savedViewCount.getViewCount()).isZero();
    }

    @Test
    @DisplayName("분리된 조회수는 현재값과 기준값 중 큰 값에서 증가한다")
    void incrementUsesGreaterCurrentOrBaselineViewCount() {
        User writer = userRepository.save(
                new User(
                        "view-count-increment@test.com",
                        "encoded-password",
                        "조회수증가",
                        "profile.png",
                        0
                )
        );
        Post post = postRepository.save(
                new Post(writer, "조회수 증가 게시글", "본문")
        );
        entityManager.flush();

        int firstUpdatedRows =
                postViewCountRepository.incrementViewCount(
                        post.getPostId(),
                        100L
                );
        int secondUpdatedRows =
                postViewCountRepository.incrementViewCount(
                        post.getPostId(),
                        50L
                );

        PostViewCount savedViewCount = postViewCountRepository
                .findById(post.getPostId())
                .orElseThrow();

        assertThat(firstUpdatedRows).isEqualTo(1);
        assertThat(secondUpdatedRows).isEqualTo(1);
        assertThat(savedViewCount.getViewCount()).isEqualTo(102L);
    }

    @Test
    @DisplayName("Redis 스냅샷은 기존 조회수를 감소시키지 않고 반영된다")
    void persistMaxDoesNotDecreaseViewCount() {
        User writer = userRepository.save(
                new User(
                        "view-count-persist@test.com",
                        "encoded-password",
                        "조회수반영",
                        "profile.png",
                        0
                )
        );
        Post post = postRepository.save(
                new Post(writer, "조회수 반영 게시글", "본문")
        );
        entityManager.flush();

        postViewCountRepository.persistMaxViewCount(
                post.getPostId(),
                150L
        );
        postViewCountRepository.persistMaxViewCount(
                post.getPostId(),
                120L
        );

        PostViewCount savedViewCount = postViewCountRepository
                .findById(post.getPostId())
                .orElseThrow();

        assertThat(savedViewCount.getViewCount()).isEqualTo(150L);
    }
}
