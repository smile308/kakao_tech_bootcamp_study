// 게시글 상세 조회용 읽기 서비스의 데이터 수집을 검증하는 테스트
package kr.adapterz.springdatajpa.service;

import kr.adapterz.springdatajpa.entity.Comment;
import kr.adapterz.springdatajpa.entity.Post;
import kr.adapterz.springdatajpa.entity.User;
import kr.adapterz.springdatajpa.exception.AuthException;
import kr.adapterz.springdatajpa.exception.DataNullException;
import kr.adapterz.springdatajpa.repository.CommentRepository;
import kr.adapterz.springdatajpa.repository.LikeRepository;
import kr.adapterz.springdatajpa.repository.PostReportRepository;
import kr.adapterz.springdatajpa.repository.PostRepository;
import kr.adapterz.springdatajpa.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PostViewReadServiceTest {

    @Mock
    private PostRepository postRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private CommentRepository commentRepository;

    @Mock
    private LikeRepository likeRepository;

    @Mock
    private PostReportRepository postReportRepository;

    @InjectMocks
    private PostViewReadService postViewReadService;

    @Test
    void 게시글이_없으면_No_Post_예외가_발생한다() {
        when(postRepository.findByPostIdAndDeletedFalse(1L))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> postViewReadService.read(1L, 2L))
                .isInstanceOf(DataNullException.class)
                .hasMessage("No_Post");
    }

    @Test
    void 신고로_차단된_게시글은_조회할_수_없다() {
        Post post = createPost(1L, createUser(1L));
        increaseReportCount(post, Post.REPORT_BLOCK_THRESHOLD);

        when(postRepository.findByPostIdAndDeletedFalse(1L))
                .thenReturn(Optional.of(post));

        assertThatThrownBy(() -> postViewReadService.read(1L, 2L))
                .isInstanceOf(DataNullException.class)
                .hasMessage("No_Post");
    }

    @Test
    void 로그인_사용자가_없으면_No_User_예외가_발생한다() {
        Post post = createPost(1L, createUser(1L));

        when(postRepository.findByPostIdAndDeletedFalse(1L))
                .thenReturn(Optional.of(post));
        when(userRepository.findByUserIdAndDeletedFalse(2L))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> postViewReadService.read(1L, 2L))
                .isInstanceOf(AuthException.class)
                .hasMessage("No_User");
    }

    @Test
    void 상세_조회에_필요한_데이터와_기준_조회수를_수집한다() {
        User writer = createUser(1L);
        User viewer = createUser(2L);
        Post post = createPost(1L, writer);
        Comment comment = new Comment(viewer, post, "댓글");

        when(postRepository.findByPostIdAndDeletedFalse(1L))
                .thenReturn(Optional.of(post));
        when(userRepository.findByUserIdAndDeletedFalse(2L))
                .thenReturn(Optional.of(viewer));
        when(commentRepository.findByPostWithUser(post))
                .thenReturn(List.of(comment));
        when(likeRepository.existsByPostAndUser(post, viewer))
                .thenReturn(true);
        when(postReportRepository.existsByPostAndUser(post, viewer))
                .thenReturn(false);

        PostViewReadService.PostViewData result =
                postViewReadService.read(1L, 2L);

        assertThat(result.post()).isSameAs(post);
        assertThat(result.counter()).isSameAs(post.getPostCounter());
        assertThat(result.baselineViewCount()).isZero();
        assertThat(result.comments()).hasSize(1);
        assertThat(result.liked()).isTrue();
        assertThat(result.reported()).isFalse();
        assertThat(result.mine()).isFalse();
    }

    private User createUser(Long userId) {
        User user = new User(
                "test" + userId + "@test.com",
                "Password1!",
                "tester" + userId,
                "profile.png",
                0
        );
        ReflectionTestUtils.setField(user, "userId", userId);
        return user;
    }

    private Post createPost(Long postId, User user) {
        Post post = new Post(user, "title", "content");
        ReflectionTestUtils.setField(post, "postId", postId);
        return post;
    }

    private void increaseReportCount(Post post, int count) {
        for (int index = 0; index < count; index++) {
            post.report();
        }
    }
}
