package kr.adapterz.springdatajpa.repository;

import jakarta.persistence.EntityManager;
import kr.adapterz.springdatajpa.entity.Post;
import kr.adapterz.springdatajpa.entity.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional
class PostRepositoryIntegrationTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PostRepository postRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    @DisplayName("목록 조회에서 신고 4회 게시글은 노출하고 5회 게시글은 제외한다")
    void reportedPostIsExcludedFromListAtThreshold() {
        User writer = userRepository.save(
                new User(
                        "report-list-writer@test.com",
                        "encoded-password",
                        "신고목록작성자",
                        "profile.png",
                        0
                )
        );

        Post visiblePost = new Post(writer, "신고 4회 게시글", "노출되는 본문");
        increaseReportCount(visiblePost, Post.REPORT_BLOCK_THRESHOLD - 1);

        Post hiddenPost = new Post(writer, "신고 5회 게시글", "숨겨지는 본문");
        increaseReportCount(hiddenPost, Post.REPORT_BLOCK_THRESHOLD);

        postRepository.save(visiblePost);
        postRepository.save(hiddenPost);
        entityManager.flush();
        entityManager.clear();

        Page<Post> result = postRepository
                .findByDeletedFalseAndReportCountLessThanOrderByPostIdDesc(
                        Post.REPORT_BLOCK_THRESHOLD,
                        PageRequest.of(0, 100)
                );

        assertThat(result.getContent())
                .extracting(Post::getPostId)
                .contains(visiblePost.getPostId())
                .doesNotContain(hiddenPost.getPostId());
    }

    private void increaseReportCount(Post post, int count) {
        for (int i = 0; i < count; i++) {
            post.report();
        }
    }
}
