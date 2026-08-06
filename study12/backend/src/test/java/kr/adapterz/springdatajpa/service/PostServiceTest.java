package kr.adapterz.springdatajpa.service;

import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import kr.adapterz.springdatajpa.dto.post.PostFixRequestDto;
import kr.adapterz.springdatajpa.dto.post.PostDeleteRequestDto;
import kr.adapterz.springdatajpa.dto.post.PostPageResponseDto;
import kr.adapterz.springdatajpa.dto.post.PostViewResponseDto;
import kr.adapterz.springdatajpa.entity.*;
import kr.adapterz.springdatajpa.exception.AuthException;
import kr.adapterz.springdatajpa.exception.DataNullException;
import kr.adapterz.springdatajpa.exception.ForbiddenException;
import kr.adapterz.springdatajpa.exception.InvalidRequestException;
import kr.adapterz.springdatajpa.repository.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.dao.DataIntegrityViolationException;
import kr.adapterz.springdatajpa.dto.post.PostReportResponseDto;
import kr.adapterz.springdatajpa.repository.PostReportRepository;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PostServiceTest {

    private static final String SAME_IMAGE = "data:image/png;base64,iVBORw0KGgo=";
    private static final String NEW_IMAGE = "data:image/png;base64,iVBORw0KGgoA";

    @Mock
    private PostRepository postRepository;

    @Mock
    private PostCounterRepository postCounterRepository;

    @Mock
    private ViewCountUpdater viewCountUpdater;

    @Mock
    private UserRepository userRepository;

    @Mock
    private CommentRepository commentRepository;

    @Mock
    private LikeRepository likeRepository;

    @Mock
    private EntityManager entityManager;

    @InjectMocks
    private PostService postService;

    @Mock
    private PostReportRepository postReportRepository;

    @Test
    void 게시글_목록의_page가_음수이면_Invalid_Page_예외가_발생한다() {
        assertThatThrownBy(() -> postService.getPostList(-1, 10))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessage("Invalid_Page");

        verifyNoInteractions(postRepository);
    }

    @Test
    void 게시글_목록의_size가_1보다_작으면_Invalid_Page_Size_예외가_발생한다() {
        assertThatThrownBy(() -> postService.getPostList(0, 0))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessage("Invalid_Page_Size");

        verifyNoInteractions(postRepository);
    }

    @Test
    void 게시글_목록의_size에는_최댓값을_적용하지_않는다() {
        when(postRepository.findByDeletedFalseAndReportCountLessThanOrderByPostIdDesc(
                anyInt(),
                any()
        )).thenReturn(Page.empty());

        PostPageResponseDto response = postService.getPostList(0, 10_000);

        assertThat(response.getPosts()).isEmpty();
        assertThat(response.isHasNextPage()).isFalse();
    }

    @Test
    void 게시글_상세_조회_시_게시글이_없으면_No_Post_예외가_발생한다() {
        Long postId = 1L;
        Long loginUserId = 1L;

        when(postRepository.findByPostIdAndDeletedFalse(postId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> postService.getPostView(postId,loginUserId))
                .isInstanceOf(DataNullException.class)
                .hasMessage("No_Post");

        verify(viewCountUpdater, never()).increment(postId, 0L);
        verify(commentRepository, never()).findByPostWithUser(any(Post.class));
    }

    @Test
    void 신고로_차단된_게시글은_조회수를_증가시키지_않는다() {
        Long postId = 1L;
        Long loginUserId = 2L;
        Post post = createPost(postId, createUser(1L));

        for (int count = 0;
             count < Post.REPORT_BLOCK_THRESHOLD;
             count++) {
            post.report();
        }

        when(postRepository.findByPostIdAndDeletedFalse(postId))
                .thenReturn(Optional.of(post));

        assertThatThrownBy(
                () -> postService.getPostView(postId, loginUserId)
        )
                .isInstanceOf(DataNullException.class)
                .hasMessage("No_Post");

        verify(viewCountUpdater, never()).increment(postId, 0L);
        verify(userRepository, never())
                .findByUserIdAndDeletedFalse(loginUserId);
    }

    @Test
    void 로그인_사용자가_없으면_조회수를_증가시키지_않는다() {
        Long postId = 1L;
        Long loginUserId = 2L;
        Post post = createPost(postId, createUser(1L));

        when(postRepository.findByPostIdAndDeletedFalse(postId))
                .thenReturn(Optional.of(post));
        when(userRepository.findByUserIdAndDeletedFalse(loginUserId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(
                () -> postService.getPostView(postId, loginUserId)
        )
                .isInstanceOf(AuthException.class)
                .hasMessage("No_User");

        verify(viewCountUpdater, never()).increment(postId, 0L);
        verify(commentRepository, never()).findByPostWithUser(post);
    }

    @Test
    void 게시글_신고_성공_시_신고_이력이_저장되고_게시글과_작성자의_신고_수가_증가한다() {
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

        PostReportResponseDto response =
                postService.reportPost(postId, reporterId);

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
    void 게시글_신고_시_게시글이_없으면_No_Post_예외가_발생한다() {
        Long postId = 1L;
        Long reporterId = 2L;

        when(postRepository.findActivePostForUpdate(postId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> postService.reportPost(postId, reporterId))
                .isInstanceOf(DataNullException.class)
                .hasMessage("No_Post");

        verify(postReportRepository, never()).save(any(PostReport.class));
    }

    @Test
    void 게시글_신고_시_로그인_유저가_없으면_No_User_예외가_발생한다() {
        Long postId = 1L;
        Long writerId = 1L;
        Long reporterId = 2L;

        User writer = createUser(writerId);
        Post post = createPost(postId, writer);

        when(postRepository.findActivePostForUpdate(postId))
                .thenReturn(Optional.of(post));

        when(userRepository.findByUserIdAndDeletedFalse(reporterId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> postService.reportPost(postId, reporterId))
                .isInstanceOf(AuthException.class)
                .hasMessage("No_User");

        verify(postReportRepository, never()).save(any(PostReport.class));
    }

    @Test
    void 자기_게시글은_신고할_수_없다() {
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

        assertThatThrownBy(() -> postService.reportPost(postId, loginUserId))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessage("Cannot_Report_Own_Post");

        verify(postReportRepository, never()).save(any(PostReport.class));
    }

    @Test
    void 이미_신고한_게시글이면_Already_Reported_예외가_발생한다() {
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

        assertThatThrownBy(() -> postService.reportPost(postId, reporterId))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessage("Already_Reported");

        verify(postReportRepository, never()).save(any(PostReport.class));
    }

    @Test
    void 게시글_수정_시_작성자가_아니면_권한_예외가_발생한다() {
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
    void 신고가_5회_누적된_게시글은_작성자도_수정할_수_없다() {
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
    void 신고가_4회인_게시글은_작성자가_수정할_수_있다() {
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
    void 게시글의_제목_내용_이미지가_모두_같으면_버전을_증가시키지_않는다() {
        Long postId = 1L;
        Long writerId = 1L;
        Post post = createPost(postId, createUser(writerId));
        post.replaceImages(List.of(SAME_IMAGE));
        PostFixRequestDto request = createPostFixRequest(
                "title",
                "content",
                List.of(SAME_IMAGE)
        );

        when(postRepository.findByPostIdAndDeletedFalse(postId))
                .thenReturn(Optional.of(post));

        postService.fixPost(postId, writerId, request);

        verify(entityManager, never()).lock(any(Post.class), any(LockModeType.class));
        assertThat(post.getPostTitle()).isEqualTo("title");
        assertThat(post.getPostContent()).isEqualTo("content");
        assertThat(post.getPostImages().get(0).getImageFile()).isEqualTo(SAME_IMAGE);
    }

    @Test
    void 제목과_내용이_같아도_이미지가_다르면_실제_수정으로_처리한다() {
        Long postId = 1L;
        Long writerId = 1L;
        Post post = createPost(postId, createUser(writerId));
        post.replaceImages(List.of(SAME_IMAGE));
        PostFixRequestDto request = createPostFixRequest(
                "title",
                "content",
                List.of(NEW_IMAGE)
        );

        when(postRepository.findByPostIdAndDeletedFalse(postId))
                .thenReturn(Optional.of(post));

        postService.fixPost(postId, writerId, request);

        verify(entityManager).lock(post, LockModeType.OPTIMISTIC_FORCE_INCREMENT);
        assertThat(post.getPostImages().get(0).getImageFile()).isEqualTo(NEW_IMAGE);
    }

    @Test
    void 게시글_삭제_시_게시글이_없으면_No_Post_예외가_발생한다() {
        Long postId = 1L;
        Long loginUserId = 1L;

        when(postRepository.findByPostIdAndDeletedFalse(postId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> postService.deletePost(
                postId,
                loginUserId,
                createPostDeleteRequest(null)
        ))
                .isInstanceOf(DataNullException.class)
                .hasMessage("No_Post");

        verify(postRepository).findByPostIdAndDeletedFalse(postId);
    }

    @Test
    void 게시글_삭제_시_작성자가_아니면_권한_예외가_발생한다() {
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
                        loginUserId,
                        createPostDeleteRequest(post.getVersion())
                )
        )
                .isInstanceOf(ForbiddenException.class)
                .hasMessage("Forbidden_Access");

        assertThat(post.isDeleted()).isFalse();
    }

    @Test
    void 신고가_5회_누적된_게시글은_작성자도_삭제할_수_없다() {
        Long postId = 1L;
        Long writerId = 1L;
        Post post = createPost(postId, createUser(writerId));
        increaseReportCount(post, Post.REPORT_BLOCK_THRESHOLD);

        when(postRepository.findByPostIdAndDeletedFalse(postId))
                .thenReturn(Optional.of(post));

        assertThatThrownBy(() -> postService.deletePost(
                postId,
                writerId,
                createPostDeleteRequest(post.getVersion())
        ))
                .isInstanceOf(ForbiddenException.class)
                .hasMessage("Forbidden_Access");

        assertThat(post.isDeleted()).isFalse();
    }

    @Test
    void 신고가_4회인_게시글은_작성자가_삭제할_수_있다() {
        Long postId = 1L;
        Long writerId = 1L;
        Post post = createPost(postId, createUser(writerId));
        increaseReportCount(post, Post.REPORT_BLOCK_THRESHOLD - 1);

        when(postRepository.findByPostIdAndDeletedFalse(postId))
                .thenReturn(Optional.of(post));
        when(postCounterRepository.findByPostIdForUpdate(postId))
                .thenReturn(Optional.of(post.getPostCounter()));

        postService.deletePost(
                postId,
                writerId,
                createPostDeleteRequest(post.getVersion())
        );

        assertThat(post.isDeleted()).isTrue();
    }

    @Test
    void 좋아요_시_로그인_유저가_없으면_No_User_예외가_발생한다() {
        Long postId = 1L;
        Long loginUserId = 1L;
        Post post = createPost(postId, createUser(2L));

        when(postRepository.findActivePostForInteraction(postId))
                .thenReturn(Optional.of(post));
        when(userRepository.findByUserIdAndDeletedFalse(loginUserId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> postService.likePost(postId, loginUserId))
                .isInstanceOf(AuthException.class)
                .hasMessage("No_User");

        verify(likeRepository, never()).saveAndFlush(any(Like.class));
    }

    @Test
    void 이미_좋아요한_게시글이면_Already_Liked_예외가_발생한다() {
        Long postId = 1L;
        Long loginUserId = 1L;
        User loginUser = createUser(loginUserId);
        Post post = createPost(postId, createUser(2L));

        when(postRepository.findActivePostForInteraction(postId))
                .thenReturn(Optional.of(post));
        when(userRepository.findByUserIdAndDeletedFalse(loginUserId))
                .thenReturn(Optional.of(loginUser));
        when(postCounterRepository.findByPostIdForUpdate(postId))
                .thenReturn(Optional.of(post.getPostCounter()));
        when(postRepository.findActivePostForInteractionCheck(postId))
                .thenReturn(Optional.of(post));
        when(likeRepository.saveAndFlush(any(Like.class)))
                .thenThrow(new DataIntegrityViolationException("duplicate"));

        assertThatThrownBy(() -> postService.likePost(postId, loginUserId))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessage("Already_Liked");

        verify(postCounterRepository, never()).incrementLikeCount(postId);
    }

    @Test
    void 좋아요_취소_시_좋아요_내역이_없으면_Not_Liked_예외가_발생한다() {
        Long postId = 1L;
        Long loginUserId = 1L;
        User loginUser = createUser(loginUserId);
        Post post = createPost(postId, createUser(2L));

        when(postRepository.findActivePostForInteraction(postId))
                .thenReturn(Optional.of(post));
        when(userRepository.findByUserIdAndDeletedFalse(loginUserId))
                .thenReturn(Optional.of(loginUser));
        when(postCounterRepository.findByPostIdForUpdate(postId))
                .thenReturn(Optional.of(post.getPostCounter()));
        when(postRepository.findActivePostForInteractionCheck(postId))
                .thenReturn(Optional.of(post));

        assertThatThrownBy(() -> postService.cancelLike(postId, loginUserId))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessage("Not_Liked");

        verify(postCounterRepository, never()).decrementLikeCount(postId);
    }

    @Test
    void 게시글_상세_조회_시_본인_게시글과_댓글의_isMine이_true로_반환된다() {
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
        when(viewCountUpdater.increment(postId, 0L))
                .thenReturn(1L);

        PostViewResponseDto response =
                postService.getPostView(postId, loginUserId);

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
        verify(viewCountUpdater).increment(postId, 0L);

        verify(postRepository)
                .findByPostIdAndDeletedFalse(postId);

        verify(userRepository)
                .findByUserIdAndDeletedFalse(loginUserId);

        verify(commentRepository)
                .findByPostWithUser(post);
    }

    @Test
    void 게시글_상세_조회_시_다른_사람의_게시글이면_isMine이_false로_반환된다() {
        Long postId = 1L;
        Long writerId = 1L;
        Long loginUserId = 2L;

        User writer = createUser(writerId);
        User loginUser = createUser(loginUserId);

        Post post = createPost(postId, writer);

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
        when(viewCountUpdater.increment(postId, 0L))
                .thenReturn(1L);

        PostViewResponseDto response =
                postService.getPostView(postId, loginUserId);

        assertThat(response.getIsMine()).isFalse();
        assertThat(response.getComments()).isEmpty();
        assertThat(response.getViewCount()).isEqualTo(1L);
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
        ReflectionTestUtils.setField(request, "version", null);
        ReflectionTestUtils.setField(request, "title", title);
        ReflectionTestUtils.setField(request, "content", content);
        return request;
    }

    private PostFixRequestDto createPostFixRequest(
            String title,
            String content,
            List<String> imageFiles
    ) {
        PostFixRequestDto request = createPostFixRequest(title, content);
        ReflectionTestUtils.setField(request, "imageFiles", imageFiles);
        return request;
    }

    private PostDeleteRequestDto createPostDeleteRequest(Long version) {
        PostDeleteRequestDto request = new PostDeleteRequestDto();
        ReflectionTestUtils.setField(request, "version", version);
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
