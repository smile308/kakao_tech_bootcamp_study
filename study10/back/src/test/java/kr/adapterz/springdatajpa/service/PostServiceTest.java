package kr.adapterz.springdatajpa.service;

import kr.adapterz.springdatajpa.dto.post.PostFixRequestDto;
import kr.adapterz.springdatajpa.dto.post.PostViewResponseDto;
import kr.adapterz.springdatajpa.entity.*;
import kr.adapterz.springdatajpa.exception.AuthException;
import kr.adapterz.springdatajpa.exception.DataNullException;
import kr.adapterz.springdatajpa.exception.ForbiddenException;
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
import kr.adapterz.springdatajpa.repository.PostReportRepository;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.List;
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

        when(postRepository.incrementViewCount(postId))
                .thenReturn(0);

        assertThatThrownBy(() -> postService.getPostView(postId,loginUserId))
                .isInstanceOf(DataNullException.class)
                .hasMessage("No_Post");

        verify(postRepository).incrementViewCount(postId);
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


        when(postRepository.findActivePostForUpdate(postId))
                .thenReturn(Optional.of(post));

        when(userRepository.findByUserIdForUpdate(writerId))
                .thenReturn(Optional.of(writer));

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
        assertThat(
                Arrays.stream(PostReportResponseDto.class.getDeclaredFields())
                        .map(field -> field.getName())
        ).containsExactlyInAnyOrder("message", "postId", "reportCount");

        verify(postReportRepository).save(any(PostReport.class));
    }

    @Test
    @DisplayName("게시글 신고 시 게시글이 없으면 No_Post 예외가 발생한다")
    void reportPostFailByNoPost() {
        // given
        Long postId = 1L;
        Long reporterId = 2L;

        when(postRepository.findActivePostForUpdate(postId))
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

        when(postRepository.findActivePostForUpdate(postId))
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

        when(postRepository.findActivePostForUpdate(postId))
                .thenReturn(Optional.of(post));

        when(userRepository.findByUserIdAndDeletedFalse(loginUserId))
                .thenReturn(Optional.of(writer));

        when(userRepository.findByUserIdForUpdate(loginUserId))
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

        when(postRepository.findActivePostForUpdate(postId))
                .thenReturn(Optional.of(post));

        when(userRepository.findByUserIdForUpdate(writerId))
                .thenReturn(Optional.of(writer));

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
    @DisplayName("게시글 수정 시 작성자가 아니면 권한 예외가 발생한다")
    void fixPostFailByForbidden() {
        Long postId = 1L;
        Long writerId = 1L;
        Long loginUserId = 2L;

        Post post =
                createPost(postId, createUser(writerId));

        PostFixRequestDto request =
                createPostFixRequest(
                        "new title",
                        "new content"
                );

        when(postRepository.findByPostIdAndDeletedFalse(postId))
                .thenReturn(Optional.of(post));

        assertThatThrownBy(
                () -> postService.fixPost(
                        postId,
                        loginUserId,
                        request
                )
        )
                .isInstanceOf(ForbiddenException.class)
                .hasMessage("Forbidden_Access");

        assertThat(post.getPostTitle())
                .isEqualTo("title");

        assertThat(post.getPostContent())
                .isEqualTo("content");
    }

    @Test
    @DisplayName("신고가 5회 누적된 게시글은 작성자도 수정할 수 없다")
    void fixPostFailWhenReportCountReachesThreshold() {
        Long postId = 1L;
        Long writerId = 1L;
        Post post = createPost(postId, createUser(writerId));
        increaseReportCount(post, Post.REPORT_BLOCK_THRESHOLD);
        PostFixRequestDto request = createPostFixRequest("new title", "new content");

        when(postRepository.findByPostIdAndDeletedFalse(postId))
                .thenReturn(Optional.of(post));

        assertThatThrownBy(() -> postService.fixPost(postId, writerId, request))
                .isInstanceOf(ForbiddenException.class)
                .hasMessage("Forbidden_Access");

        assertThat(post.getPostTitle()).isEqualTo("title");
        assertThat(post.getPostContent()).isEqualTo("content");
    }

    @Test
    @DisplayName("신고가 4회인 게시글은 작성자가 수정할 수 있다")
    void fixPostSuccessBeforeReportCountReachesThreshold() {
        Long postId = 1L;
        Long writerId = 1L;
        Post post = createPost(postId, createUser(writerId));
        increaseReportCount(post, Post.REPORT_BLOCK_THRESHOLD - 1);
        PostFixRequestDto request = createPostFixRequest("new title", "new content");

        when(postRepository.findByPostIdAndDeletedFalse(postId))
                .thenReturn(Optional.of(post));

        postService.fixPost(postId, writerId, request);

        assertThat(post.getPostTitle()).isEqualTo("new title");
        assertThat(post.getPostContent()).isEqualTo("new content");
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
    @DisplayName("게시글 삭제 시 작성자가 아니면 권한 예외가 발생한다")
    void deletePostFailByForbidden() {
        Long postId = 1L;
        Long writerId = 1L;
        Long loginUserId = 2L;

        Post post =
                createPost(postId, createUser(writerId));

        when(postRepository.findByPostIdAndDeletedFalse(postId))
                .thenReturn(Optional.of(post));

        assertThatThrownBy(
                () -> postService.deletePost(
                        postId,
                        loginUserId
                )
        )
                .isInstanceOf(ForbiddenException.class)
                .hasMessage("Forbidden_Access");

        assertThat(post.isDeleted()).isFalse();
    }

    @Test
    @DisplayName("신고가 5회 누적된 게시글은 작성자도 삭제할 수 없다")
    void deletePostFailWhenReportCountReachesThreshold() {
        Long postId = 1L;
        Long writerId = 1L;
        Post post = createPost(postId, createUser(writerId));
        increaseReportCount(post, Post.REPORT_BLOCK_THRESHOLD);

        when(postRepository.findByPostIdAndDeletedFalse(postId))
                .thenReturn(Optional.of(post));

        assertThatThrownBy(() -> postService.deletePost(postId, writerId))
                .isInstanceOf(ForbiddenException.class)
                .hasMessage("Forbidden_Access");

        assertThat(post.isDeleted()).isFalse();
    }

    @Test
    @DisplayName("신고가 4회인 게시글은 작성자가 삭제할 수 있다")
    void deletePostSuccessBeforeReportCountReachesThreshold() {
        Long postId = 1L;
        Long writerId = 1L;
        Post post = createPost(postId, createUser(writerId));
        increaseReportCount(post, Post.REPORT_BLOCK_THRESHOLD - 1);

        when(postRepository.findByPostIdAndDeletedFalse(postId))
                .thenReturn(Optional.of(post));

        postService.deletePost(postId, writerId);

        assertThat(post.isDeleted()).isTrue();
    }

    @Test
    @DisplayName("좋아요 시 로그인 유저가 없으면 No_User 예외가 발생한다")
    void likePostFailByNoUser() {
        Long postId = 1L;
        Long loginUserId = 1L;
        Post post = createPost(postId, createUser(2L));

        when(postRepository.findActivePostForUpdate(postId))
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

        when(postRepository.findActivePostForUpdate(postId))
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

        when(postRepository.findActivePostForUpdate(postId))
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

    @Test
    @DisplayName("게시글 상세 조회 시 본인 게시글과 댓글의 isMine이 true로 반환된다")
    void getPostViewSuccessByOwner() {
        // given
        Long postId = 1L;
        Long loginUserId = 1L;
        Long otherUserId = 2L;

        User loginUser = createUser(loginUserId);
        User otherUser = createUser(otherUserId);

        Post post = createPost(postId, loginUser);

        Comment myComment =
                createComment(10L, loginUser, post);

        Comment otherComment =
                createComment(11L, otherUser, post);

        when(postRepository.incrementViewCount(postId))
                .thenAnswer(invocation -> {
                    post.view();
                    return 1;
                });

        when(postRepository.findByPostIdAndDeletedFalse(postId))
                .thenReturn(Optional.of(post));

        when(userRepository.findByUserIdAndDeletedFalse(loginUserId))
                .thenReturn(Optional.of(loginUser));

        when(commentRepository.findByPostWithUser(post))
                .thenReturn(List.of(myComment, otherComment));

        when(likeRepository.existsByPostAndUser(post, loginUser))
                .thenReturn(false);

        when(postReportRepository.existsByPostAndUser(post, loginUser))
                .thenReturn(false);

        // when
        PostViewResponseDto response =
                postService.getPostView(postId, loginUserId);

        // then
        assertThat(response.getIsMine()).isTrue();

        assertThat(response.getComments()).hasSize(2);

        assertThat(response.getComments().get(0).getCreatedAt())
                .isNotNull();

        assertThat(
                response.getComments().get(0).getIsMine()
        ).isTrue();

        assertThat(
                response.getComments().get(1).getIsMine()
        ).isFalse();

        assertThat(response.getViewCount()).isEqualTo(1);

        verify(postRepository)
                .findByPostIdAndDeletedFalse(postId);

        verify(userRepository)
                .findByUserIdAndDeletedFalse(loginUserId);

        verify(commentRepository)
                .findByPostWithUser(post);
    }

    @Test
    @DisplayName("게시글 상세 조회 시 다른 사람의 게시글이면 isMine이 false로 반환된다")
    void getPostViewSuccessByNonOwner() {
        // given
        Long postId = 1L;
        Long writerId = 1L;
        Long loginUserId = 2L;

        User writer = createUser(writerId);
        User loginUser = createUser(loginUserId);

        Post post = createPost(postId, writer);

        when(postRepository.incrementViewCount(postId))
                .thenAnswer(invocation -> {
                    post.view();
                    return 1;
                });

        when(postRepository.findByPostIdAndDeletedFalse(postId))
                .thenReturn(Optional.of(post));

        when(userRepository.findByUserIdAndDeletedFalse(loginUserId))
                .thenReturn(Optional.of(loginUser));

        when(commentRepository.findByPostWithUser(post))
                .thenReturn(List.of());

        when(likeRepository.existsByPostAndUser(post, loginUser))
                .thenReturn(false);

        when(postReportRepository.existsByPostAndUser(post, loginUser))
                .thenReturn(false);

        // when
        PostViewResponseDto response =
                postService.getPostView(postId, loginUserId);

        // then
        assertThat(response.getIsMine()).isFalse();
        assertThat(response.getComments()).isEmpty();
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

    private PostFixRequestDto createPostFixRequest(String title, String content) {
        PostFixRequestDto request = new PostFixRequestDto();
        ReflectionTestUtils.setField(request, "title", title);
        ReflectionTestUtils.setField(request, "content", content);
        return request;
    }

    private Comment createComment(Long commentId, User user, Post post) {
        Comment comment =
                new Comment(user, post, "comment");
        ReflectionTestUtils.setField(comment, "commentId", commentId);
        return comment;
    }

    private void increaseReportCount(Post post, int count) {
        for (int i = 0; i < count; i++) {
            post.report();
        }
    }
}
