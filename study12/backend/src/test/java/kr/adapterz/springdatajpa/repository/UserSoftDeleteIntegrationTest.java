package kr.adapterz.springdatajpa.repository;

import jakarta.persistence.EntityManager;
import kr.adapterz.springdatajpa.entity.Post;
import kr.adapterz.springdatajpa.entity.User;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

@ActiveProfiles("test")
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
    void 탈퇴한_사용자는_활성_사용자_조회에서_제외된다() {
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
    void 작성자가_탈퇴해도_작성자의_게시글_목록과_상세를_조회할_수_있다() {
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
                .findByDeletedFalseAndReportCountLessThanOrderByPostIdDesc(
                        Post.REPORT_BLOCK_THRESHOLD,
                        PageRequest.of(0, 100)
                )
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
