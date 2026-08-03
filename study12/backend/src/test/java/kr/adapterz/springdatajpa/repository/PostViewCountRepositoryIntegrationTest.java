package kr.adapterz.springdatajpa.repository;

import jakarta.persistence.EntityManager;
import kr.adapterz.springdatajpa.entity.Post;
import kr.adapterz.springdatajpa.entity.PostViewCount;
import kr.adapterz.springdatajpa.entity.User;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

@ActiveProfiles("test")
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
    void 게시글_저장_시_조회수_카운터도_같은_식별자로_저장된다() {
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
    void 분리된_조회수는_현재값과_기준값_중_큰_값에서_증가한다() {
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
    void Redis_스냅샷은_기존_조회수를_감소시키지_않고_반영된다() {
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
