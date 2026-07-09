package kr.adapterz.springdatajpa.service;

import kr.adapterz.springdatajpa.dto.post.PostFixRequestDto;
import kr.adapterz.springdatajpa.entity.Like;
import kr.adapterz.springdatajpa.entity.Post;
import kr.adapterz.springdatajpa.entity.User;
import kr.adapterz.springdatajpa.exception.AuthException;
import kr.adapterz.springdatajpa.exception.DataNullException;
import kr.adapterz.springdatajpa.exception.InvalidRequestException;
import kr.adapterz.springdatajpa.repository.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import kr.adapterz.springdatajpa.dto.post.PostReportResponseDto;
import kr.adapterz.springdatajpa.entity.PostReport;
import kr.adapterz.springdatajpa.repository.PostReportRepository;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PostServiceTest {

    @Mock
    private PostRepository postRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private CommentRepository commentRepository;

    @Mock
    private LikeRepository likeRepository;

    @InjectMocks
    private PostService postService;

    @Mock
    private PostReportRepository postReportRepository;

    @Test
    @DisplayName("게시글 상세 조회 시 게시글이 없으면 No_Post 예외가 발생한다")
    void getPostViewFailByNoPost() {
        Long postId = 1L;
        Long loginUserId = 1L;

        when(postRepository.findByPostIdAndDeletedFalse(postId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> postService.getPostView(postId,loginUserId))
                .isInstanceOf(DataNullException.class)
                .hasMessage("No_Post");

        verify(postRepository).findByPostIdAndDeletedFalse(postId);
        verify(commentRepository, never()).findByPostWithUser(any(Post.class));
    }

    @Test
    @DisplayName("게시글 신고 성공 시 신고 이력이 저장되고 게시글과 작성자의 신고 수가 증가한다")
    void reportPostSuccess() {
        // given
        Long postId = 1L;
        Long writerId = 1L;
        Long reporterId = 2L;

        User writer = createUser(writerId);
        User reporter = createUser(reporterId);
        Post post = createPost(postId, writer);


        when(postRepository.findByPostIdAndDeletedFalse(postId))
                .thenReturn(Optional.of(post));

        when(userRepository.findByUserIdAndDeletedFalse(reporterId))
                .thenReturn(Optional.of(reporter));

        when(postReportRepository.existsByPostAndUser(post, reporter))
                .thenReturn(false);

        // when
        PostReportResponseDto response =
                postService.reportPost(postId, reporterId);

        // then
        assertThat(post.getReportCount()).isEqualTo(1);
        assertThat(writer.getReceivedReportCount()).isEqualTo(1);

        assertThat(response.getMessage()).isEqualTo("report_success");
        assertThat(response.getPostId()).isEqualTo(postId);
        assertThat(response.getReportCount()).isEqualTo(1);
        assertThat(response.getWriterUserId()).isEqualTo(writerId);
        assertThat(response.getWriterReceivedReportCount()).isEqualTo(1);

        verify(postReportRepository).save(any(PostReport.class));
    }

    @Test
    @DisplayName("게시글 신고 시 게시글이 없으면 No_Post 예외가 발생한다")
    void reportPostFailByNoPost() {
        // given
        Long postId = 1L;
        Long reporterId = 2L;

        when(postRepository.findByPostIdAndDeletedFalse(postId))
                .thenReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> postService.reportPost(postId, reporterId))
                .isInstanceOf(DataNullException.class)
                .hasMessage("No_Post");

        verify(postReportRepository, never()).save(any(PostReport.class));
    }

    @Test
    @DisplayName("게시글 신고 시 로그인 유저가 없으면 No_User 예외가 발생한다")
    void reportPostFailByNoUser() {
        // given
        Long postId = 1L;
        Long writerId = 1L;
        Long reporterId = 2L;

        User writer = createUser(writerId);
        Post post = createPost(postId, writer);

        when(postRepository.findByPostIdAndDeletedFalse(postId))
                .thenReturn(Optional.of(post));

        when(userRepository.findByUserIdAndDeletedFalse(reporterId))
                .thenReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> postService.reportPost(postId, reporterId))
                .isInstanceOf(AuthException.class)
                .hasMessage("No_User");

        verify(postReportRepository, never()).save(any(PostReport.class));
    }

    @Test
    @DisplayName("자기 게시글은 신고할 수 없다")
    void reportPostFailByOwnPost() {
        // given
        Long postId = 1L;
        Long loginUserId = 1L;

        User writer = createUser(loginUserId);
        Post post = createPost(postId, writer);

        when(postRepository.findByPostIdAndDeletedFalse(postId))
                .thenReturn(Optional.of(post));

        when(userRepository.findByUserIdAndDeletedFalse(loginUserId))
                .thenReturn(Optional.of(writer));

        // when & then
        assertThatThrownBy(() -> postService.reportPost(postId, loginUserId))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessage("Cannot_Report_Own_Post");

        verify(postReportRepository, never()).save(any(PostReport.class));
    }

    @Test
    @DisplayName("이미 신고한 게시글이면 Already_Reported 예외가 발생한다")
    void reportPostFailByAlreadyReported() {
        // given
        Long postId = 1L;
        Long writerId = 1L;
        Long reporterId = 2L;

        User writer = createUser(writerId);
        User reporter = createUser(reporterId);
        Post post = createPost(postId, writer);

        when(postRepository.findByPostIdAndDeletedFalse(postId))
                .thenReturn(Optional.of(post));

        when(userRepository.findByUserIdAndDeletedFalse(reporterId))
                .thenReturn(Optional.of(reporter));

        when(postReportRepository.existsByPostAndUser(post, reporter))
                .thenReturn(true);

        // when & then
        assertThatThrownBy(() -> postService.reportPost(postId, reporterId))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessage("Already_Reported");

        verify(postReportRepository, never()).save(any(PostReport.class));
    }

    @Test
    @DisplayName("게시글 수정 시 작성자가 아니면 No_Auth 예외가 발생한다")
    void fixPostFailByNoAuth() {
        Long postId = 1L;
        Long writerId = 1L;
        Long loginUserId = 2L;
        Post post = createPost(postId, createUser(writerId));
        PostFixRequestDto request = createPostFixRequest("new title", "new content");

        when(postRepository.findByPostIdAndDeletedFalse(postId))
                .thenReturn(Optional.of(post));

        assertThatThrownBy(() -> postService.fixPost(postId, loginUserId, request))
                .isInstanceOf(AuthException.class)
                .hasMessage("No_Auth");

        verify(postRepository).findByPostIdAndDeletedFalse(postId);
    }

    @Test
    @DisplayName("게시글 삭제 시 게시글이 없으면 No_Post 예외가 발생한다")
    void deletePostFailByNoPost() {
        Long postId = 1L;
        Long loginUserId = 1L;

        when(postRepository.findByPostIdAndDeletedFalse(postId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> postService.deletePost(postId, loginUserId))
                .isInstanceOf(DataNullException.class)
                .hasMessage("No_Post");

        verify(postRepository).findByPostIdAndDeletedFalse(postId);
    }

    @Test
    @DisplayName("게시글 삭제 시 작성자가 아니면 No_Auth 예외가 발생한다")
    void deletePostFailByNoAuth() {
        Long postId = 1L;
        Long writerId = 1L;
        Long loginUserId = 2L;
        Post post = createPost(postId, createUser(writerId));

        when(postRepository.findByPostIdAndDeletedFalse(postId))
                .thenReturn(Optional.of(post));

        assertThatThrownBy(() -> postService.deletePost(postId, loginUserId))
                .isInstanceOf(AuthException.class)
                .hasMessage("No_Auth");

        verify(postRepository).findByPostIdAndDeletedFalse(postId);
    }

    @Test
    @DisplayName("좋아요 시 로그인 유저가 없으면 No_User 예외가 발생한다")
    void likePostFailByNoUser() {
        Long postId = 1L;
        Long loginUserId = 1L;
        Post post = createPost(postId, createUser(2L));

        when(postRepository.findByPostIdAndDeletedFalse(postId))
                .thenReturn(Optional.of(post));
        when(userRepository.findByUserIdAndDeletedFalse(loginUserId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> postService.likePost(postId, loginUserId))
                .isInstanceOf(AuthException.class)
                .hasMessage("No_User");

        verify(likeRepository, never()).save(any(Like.class));
    }

    @Test
    @DisplayName("이미 좋아요한 게시글이면 Already_Liked 예외가 발생한다")
    void likePostFailByAlreadyLiked() {
        Long postId = 1L;
        Long loginUserId = 1L;
        User loginUser = createUser(loginUserId);
        Post post = createPost(postId, createUser(2L));

        when(postRepository.findByPostIdAndDeletedFalse(postId))
                .thenReturn(Optional.of(post));
        when(userRepository.findByUserIdAndDeletedFalse(loginUserId))
                .thenReturn(Optional.of(loginUser));
        when(likeRepository.existsByPostAndUser(post, loginUser))
                .thenReturn(true);

        assertThatThrownBy(() -> postService.likePost(postId, loginUserId))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessage("Already_Liked");

        verify(likeRepository, never()).save(any(Like.class));
    }

    @Test
    @DisplayName("좋아요 취소 시 좋아요 내역이 없으면 Not_Liked 예외가 발생한다")
    void cancelLikeFailByNotLiked() {
        Long postId = 1L;
        Long loginUserId = 1L;
        User loginUser = createUser(loginUserId);
        Post post = createPost(postId, createUser(2L));

        when(postRepository.findByPostIdAndDeletedFalse(postId))
                .thenReturn(Optional.of(post));
        when(userRepository.findByUserIdAndDeletedFalse(loginUserId))
                .thenReturn(Optional.of(loginUser));
        when(likeRepository.findByPostAndUser(post, loginUser))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> postService.cancelLike(postId, loginUserId))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessage("Not_Liked");

        verify(likeRepository, never()).delete(any(Like.class));
    }

    private User createUser(Long userId) {
        User user = new User("test" + userId + "@test.com", "Password1!", "tester" + userId, "profile.png",0);
        ReflectionTestUtils.setField(user, "userId", userId);
        return user;
    }

    private Post createPost(Long postId, User user) {
        Post post = new Post(user, "title", "content");
        ReflectionTestUtils.setField(post, "postId", postId);
        return post;
    }

    private PostFixRequestDto createPostFixRequest(String title, String contents) {
        PostFixRequestDto request = new PostFixRequestDto();
        ReflectionTestUtils.setField(request, "title", title);
        ReflectionTestUtils.setField(request, "contents", contents);
        return request;
    }
}
