package kr.adapterz.springdatajpa.repository;

import jakarta.persistence.EntityManager;
import kr.adapterz.springdatajpa.entity.Post;
import kr.adapterz.springdatajpa.entity.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional
class UserSoftDeleteIntegrationTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PostRepository postRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    @DisplayName("탈퇴한 사용자는 활성 사용자 조회에서 제외된다")
    void deletedUserIsExcludedFromActiveUserQueries() {
        User user = userRepository.save(
                new User(
                        "deleted-user@test.com",
                        "encoded-password",
                        "탈퇴전이름",
                        "profile.png",
                        0
                )
        );

        Long userId = user.getUserId();
        user.delete();

        entityManager.flush();
        entityManager.clear();

        assertThat(userRepository.findByUserIdAndDeletedFalse(userId)).isEmpty();
        assertThat(userRepository.findByEmailAndDeletedFalse("deleted-user@test.com")).isEmpty();

        User deletedUser = userRepository.findById(userId).orElseThrow();
        assertThat(deletedUser.isDeleted()).isTrue();
        assertThat(deletedUser.getNickname()).isEqualTo("삭제된 유저");
        assertThat(deletedUser.getProfileImage()).isNull();
    }

    @Test
    @DisplayName("작성자가 탈퇴해도 작성자의 게시글 목록과 상세를 조회할 수 있다")
    void postRemainsReadableAfterWriterDeletesAccount() {
        User writer = userRepository.save(
                new User(
                        "deleted-writer@test.com",
                        "encoded-password",
                        "탈퇴전작성자",
                        "profile.png",
                        0
                )
        );

        Post post = postRepository.save(
                new Post(writer, "탈퇴 작성자의 글", "남아 있어야 하는 본문")
        );

        Long postId = post.getPostId();
        writer.delete();

        entityManager.flush();
        entityManager.clear();

        Post listPost = postRepository
                .findByDeletedFalseOrderByPostIdDesc(PageRequest.of(0, 100))
                .getContent()
                .stream()
                .filter(item -> item.getPostId().equals(postId))
                .findFirst()
                .orElseThrow();

        assertThat(listPost.getUser().getNickname()).isEqualTo("삭제된 유저");
        assertThat(listPost.getUser().getProfileImage()).isNull();

        entityManager.clear();

        Post detailPost = postRepository
                .findByPostIdAndDeletedFalse(postId)
                .orElseThrow();

        assertThat(detailPost.getUser().getNickname()).isEqualTo("삭제된 유저");
        assertThat(detailPost.getUser().getProfileImage()).isNull();
    }
}
