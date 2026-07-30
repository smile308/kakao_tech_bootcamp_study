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
}
