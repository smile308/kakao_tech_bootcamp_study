package kr.adapterz.springdatajpa.service;

import kr.adapterz.springdatajpa.dto.comment.CommentDeleteRequestDto;
import kr.adapterz.springdatajpa.dto.comment.CommentFixRequestDto;
import kr.adapterz.springdatajpa.dto.comment.CommentPostRequestDto;
import kr.adapterz.springdatajpa.entity.Comment;
import kr.adapterz.springdatajpa.entity.Post;
import kr.adapterz.springdatajpa.entity.User;
import kr.adapterz.springdatajpa.exception.AuthException;
import kr.adapterz.springdatajpa.exception.DataNullException;
import kr.adapterz.springdatajpa.exception.ForbiddenException;
import kr.adapterz.springdatajpa.repository.CommentRepository;
import kr.adapterz.springdatajpa.repository.PostCounterRepository;
import kr.adapterz.springdatajpa.repository.PostRepository;
import kr.adapterz.springdatajpa.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CommentServiceTest {

    @Mock
    private CommentRepository commentRepository;

    @Mock
    private PostRepository postRepository;

    @Mock
    private PostCounterRepository postCounterRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private CommentService commentService;

    @Test
    void 댓글_등록_시_로그인_유저가_없으면_No_Account_예외가_발생한다() {
        Long postId = 1L;
        Long loginUserId = 1L;
        CommentPostRequestDto request = createCommentPostRequest("comment");

        when(userRepository.findByUserIdAndDeletedFalse(loginUserId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> commentService.commentPost(postId, loginUserId, request))
                .isInstanceOf(DataNullException.class)
                .hasMessage("No_Account");

        verify(postRepository, never()).findActivePostForInteraction(postId);
        verify(commentRepository, never()).save(any(Comment.class));
    }

    @Test
    void 댓글_등록_시_게시글이_없으면_No_Post_예외가_발생한다() {
        Long postId = 1L;
        Long loginUserId = 1L;
        User loginUser = createUser(loginUserId);
        CommentPostRequestDto request = createCommentPostRequest("comment");

        when(userRepository.findByUserIdAndDeletedFalse(loginUserId))
                .thenReturn(Optional.of(loginUser));
        when(postRepository.findActivePostForInteraction(postId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> commentService.commentPost(postId, loginUserId, request))
                .isInstanceOf(DataNullException.class)
                .hasMessage("No_Post");

        verify(commentRepository, never()).save(any(Comment.class));
    }

    @Test
    void 댓글_수정_시_댓글이_없으면_No_Comment_예외가_발생한다() {
        Long postId = 1L;
        Long loginUserId = 1L;
        Long commentId = 10L;
        CommentFixRequestDto request = createCommentFixRequest(commentId, "fixed");

        when(postRepository.findActivePostForInteractionCheck(postId))
                .thenReturn(Optional.of(createPost(postId, createUser(loginUserId))));
        when(commentRepository.findById(commentId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> commentService.commentFix(postId, loginUserId, request))
                .isInstanceOf(DataNullException.class)
                .hasMessage("No_Comment");

        verify(commentRepository).findById(commentId);
    }

    @Test
    void 댓글_수정_시_요청_게시글과_댓글의_게시글이_다르면_No_Comment_예외가_발생한다() {
        Long requestPostId = 1L;
        Long commentPostId = 2L;
        Long loginUserId = 1L;
        Long commentId = 10L;
        User writer = createUser(loginUserId);
        Comment comment = createComment(commentId, writer, createPost(commentPostId, writer));
        CommentFixRequestDto request = createCommentFixRequest(commentId, "fixed");

        when(postRepository.findActivePostForInteractionCheck(requestPostId))
                .thenReturn(Optional.of(createPost(requestPostId, writer)));
        when(commentRepository.findById(commentId))
                .thenReturn(Optional.of(comment));

        assertThatThrownBy(() -> commentService.commentFix(requestPostId, loginUserId, request))
                .isInstanceOf(DataNullException.class)
                .hasMessage("No_Comment");
    }

    @Test
    void 댓글_수정_시_작성자가_아니면_권한_예외가_발생한다() {
        Long postId = 1L;
        Long writerId = 1L;
        Long loginUserId = 2L;
        Long commentId = 10L;

        User writer = createUser(writerId);
        Post post = createPost(postId, writer);

        Comment comment =
                createComment(commentId, writer, post);

        CommentFixRequestDto request =
                createCommentFixRequest(
                        commentId,
                        "fixed"
                );

        when(postRepository.findActivePostForInteractionCheck(postId))
                .thenReturn(Optional.of(post));
        when(commentRepository.findById(commentId))
                .thenReturn(Optional.of(comment));

        assertThatThrownBy(
                () -> commentService.commentFix(
                        postId,
                        loginUserId,
                        request
                )
        )
                .isInstanceOf(ForbiddenException.class)
                .hasMessage("Forbidden_Access");

        assertThat(comment.getCommentContent())
                .isEqualTo("comment");
    }

    @Test
    void 댓글_삭제_시_게시글이_없으면_No_Post_예외가_발생한다() {
        Long postId = 1L;
        Long loginUserId = 1L;
        CommentDeleteRequestDto request = createCommentDeleteRequest(10L);

        when(postRepository.findActivePostForInteraction(postId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> commentService.commentDelete(postId, loginUserId, request))
                .isInstanceOf(DataNullException.class)
                .hasMessage("No_Post");

        verify(commentRepository, never()).findById(request.getCommentId());
    }

    @Test
    void 댓글_삭제_시_댓글이_없으면_No_Comment_예외가_발생한다() {
        Long postId = 1L;
        Long loginUserId = 1L;
        User writer = createUser(loginUserId);
        CommentDeleteRequestDto request = createCommentDeleteRequest(10L);

        when(postRepository.findActivePostForInteraction(postId))
                .thenReturn(Optional.of(createPost(postId, writer)));
        when(commentRepository.findById(request.getCommentId()))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> commentService.commentDelete(postId, loginUserId, request))
                .isInstanceOf(DataNullException.class)
                .hasMessage("No_Comment");
    }

    @Test
    void 댓글_삭제_시_요청_게시글과_댓글의_게시글이_다르면_No_Comment_예외가_발생한다() {
        Long requestPostId = 1L;
        Long commentPostId = 2L;
        Long loginUserId = 1L;
        Long commentId = 10L;
        User writer = createUser(loginUserId);
        Comment comment = createComment(commentId, writer, createPost(commentPostId, writer));
        CommentDeleteRequestDto request = createCommentDeleteRequest(commentId);

        when(postRepository.findActivePostForInteraction(requestPostId))
                .thenReturn(Optional.of(createPost(requestPostId, writer)));
        when(commentRepository.findById(commentId))
                .thenReturn(Optional.of(comment));

        assertThatThrownBy(() -> commentService.commentDelete(requestPostId, loginUserId, request))
                .isInstanceOf(DataNullException.class)
                .hasMessage("No_Comment");
    }

    @Test
    void 댓글_삭제_시_작성자가_아니면_권한_예외가_발생한다() {
        Long postId = 1L;
        Long writerId = 1L;
        Long loginUserId = 2L;
        Long commentId = 10L;
        User writer = createUser(writerId);
        Post post = createPost(postId, writer);
        Comment comment = createComment(commentId, writer, post);
        CommentDeleteRequestDto request = createCommentDeleteRequest(commentId);

        when(postRepository.findActivePostForInteraction(postId))
                .thenReturn(Optional.of(post));
        when(commentRepository.findById(commentId))
                .thenReturn(Optional.of(comment));

        assertThatThrownBy(() -> commentService.commentDelete(postId, loginUserId, request))
                .isInstanceOf(ForbiddenException.class)
                .hasMessage("Forbidden_Access");

        verify(commentRepository, never()).delete(any(Comment.class));
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

    private Comment createComment(Long commentId, User user, Post post) {
        Comment comment = new Comment(user, post, "comment");
        ReflectionTestUtils.setField(comment, "commentId", commentId);
        return comment;
    }

    private CommentPostRequestDto createCommentPostRequest(String commentContent) {
        CommentPostRequestDto request = new CommentPostRequestDto();
        ReflectionTestUtils.setField(request, "commentContent", commentContent);
        return request;
    }

    private CommentFixRequestDto createCommentFixRequest(Long commentId, String commentContent) {
        CommentFixRequestDto request = new CommentFixRequestDto();
        ReflectionTestUtils.setField(request, "commentId", commentId);
        ReflectionTestUtils.setField(request, "commentContent", commentContent);
        return request;
    }

    private CommentDeleteRequestDto createCommentDeleteRequest(Long commentId) {
        CommentDeleteRequestDto request = new CommentDeleteRequestDto();
        ReflectionTestUtils.setField(request, "commentId", commentId);
        return request;
    }
}
