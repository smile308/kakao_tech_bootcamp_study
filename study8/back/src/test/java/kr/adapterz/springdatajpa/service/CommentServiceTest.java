package kr.adapterz.springdatajpa.service;

import kr.adapterz.springdatajpa.dto.comment.CommentDeleteRequestDto;
import kr.adapterz.springdatajpa.dto.comment.CommentFixRequestDto;
import kr.adapterz.springdatajpa.dto.comment.CommentPostRequestDto;
import kr.adapterz.springdatajpa.entity.Comment;
import kr.adapterz.springdatajpa.entity.Post;
import kr.adapterz.springdatajpa.entity.User;
import kr.adapterz.springdatajpa.exception.AuthException;
import kr.adapterz.springdatajpa.exception.DataNullException;
import kr.adapterz.springdatajpa.repository.CommentRepository;
import kr.adapterz.springdatajpa.repository.PostRepository;
import kr.adapterz.springdatajpa.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
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
    private UserRepository userRepository;

    @InjectMocks
    private CommentService commentService;

    @Test
    @DisplayName("댓글 등록 시 로그인 유저가 없으면 No_Account 예외가 발생한다")
    void commentPostFailByNoAccount() {
        Long postId = 1L;
        Long loginUserId = 1L;
        CommentPostRequestDto request = createCommentPostRequest("comment");

        when(userRepository.findByUserIdAndDeletedFalse(loginUserId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> commentService.commentPost(postId, loginUserId, request))
                .isInstanceOf(DataNullException.class)
                .hasMessage("No_Account");

        verify(postRepository, never()).findById(postId);
        verify(commentRepository, never()).save(any(Comment.class));
    }

    @Test
    @DisplayName("댓글 등록 시 게시글이 없으면 No_Post 예외가 발생한다")
    void commentPostFailByNoPost() {
        Long postId = 1L;
        Long loginUserId = 1L;
        User loginUser = createUser(loginUserId);
        CommentPostRequestDto request = createCommentPostRequest("comment");

        when(userRepository.findByUserIdAndDeletedFalse(loginUserId))
                .thenReturn(Optional.of(loginUser));
        when(postRepository.findById(postId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> commentService.commentPost(postId, loginUserId, request))
                .isInstanceOf(DataNullException.class)
                .hasMessage("No_Post");

        verify(commentRepository, never()).save(any(Comment.class));
    }

    @Test
    @DisplayName("댓글 수정 시 댓글이 없으면 No_Comment 예외가 발생한다")
    void commentFixFailByNoComment() {
        Long postId = 1L;
        Long loginUserId = 1L;
        Long commentId = 10L;
        CommentFixRequestDto request = createCommentFixRequest(commentId, "fixed");

        when(commentRepository.findById(commentId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> commentService.commentFix(postId, loginUserId, request))
                .isInstanceOf(DataNullException.class)
                .hasMessage("No_Comment");

        verify(commentRepository).findById(commentId);
    }

    @Test
    @DisplayName("댓글 수정 시 요청 게시글과 댓글의 게시글이 다르면 No_Comment 예외가 발생한다")
    void commentFixFailByPostMismatch() {
        Long requestPostId = 1L;
        Long commentPostId = 2L;
        Long loginUserId = 1L;
        Long commentId = 10L;
        User writer = createUser(loginUserId);
        Comment comment = createComment(commentId, writer, createPost(commentPostId, writer));
        CommentFixRequestDto request = createCommentFixRequest(commentId, "fixed");

        when(commentRepository.findById(commentId))
                .thenReturn(Optional.of(comment));

        assertThatThrownBy(() -> commentService.commentFix(requestPostId, loginUserId, request))
                .isInstanceOf(DataNullException.class)
                .hasMessage("No_Comment");
    }

    @Test
    @DisplayName("댓글 수정 시 작성자가 아니면 No_Auth 예외가 발생한다")
    void commentFixFailByNoAuth() {
        Long postId = 1L;
        Long writerId = 1L;
        Long loginUserId = 2L;
        Long commentId = 10L;
        User writer = createUser(writerId);
        Comment comment = createComment(commentId, writer, createPost(postId, writer));
        CommentFixRequestDto request = createCommentFixRequest(commentId, "fixed");

        when(commentRepository.findById(commentId))
                .thenReturn(Optional.of(comment));

        assertThatThrownBy(() -> commentService.commentFix(postId, loginUserId, request))
                .isInstanceOf(AuthException.class)
                .hasMessage("No_Auth");
    }

    @Test
    @DisplayName("댓글 삭제 시 게시글이 없으면 No_Post 예외가 발생한다")
    void commentDeleteFailByNoPost() {
        Long postId = 1L;
        Long loginUserId = 1L;
        CommentDeleteRequestDto request = createCommentDeleteRequest(10L);

        when(postRepository.findById(postId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> commentService.commentDelete(postId, loginUserId, request))
                .isInstanceOf(DataNullException.class)
                .hasMessage("No_Post");

        verify(commentRepository, never()).findById(request.getCommentId());
    }

    @Test
    @DisplayName("댓글 삭제 시 댓글이 없으면 No_Comment 예외가 발생한다")
    void commentDeleteFailByNoComment() {
        Long postId = 1L;
        Long loginUserId = 1L;
        User writer = createUser(loginUserId);
        CommentDeleteRequestDto request = createCommentDeleteRequest(10L);

        when(postRepository.findById(postId))
                .thenReturn(Optional.of(createPost(postId, writer)));
        when(commentRepository.findById(request.getCommentId()))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> commentService.commentDelete(postId, loginUserId, request))
                .isInstanceOf(DataNullException.class)
                .hasMessage("No_Comment");
    }

    @Test
    @DisplayName("댓글 삭제 시 요청 게시글과 댓글의 게시글이 다르면 No_Comment 예외가 발생한다")
    void commentDeleteFailByPostMismatch() {
        Long requestPostId = 1L;
        Long commentPostId = 2L;
        Long loginUserId = 1L;
        Long commentId = 10L;
        User writer = createUser(loginUserId);
        Comment comment = createComment(commentId, writer, createPost(commentPostId, writer));
        CommentDeleteRequestDto request = createCommentDeleteRequest(commentId);

        when(postRepository.findById(requestPostId))
                .thenReturn(Optional.of(createPost(requestPostId, writer)));
        when(commentRepository.findById(commentId))
                .thenReturn(Optional.of(comment));

        assertThatThrownBy(() -> commentService.commentDelete(requestPostId, loginUserId, request))
                .isInstanceOf(DataNullException.class)
                .hasMessage("No_Comment");
    }

    @Test
    @DisplayName("댓글 삭제 시 작성자가 아니면 No_Auth 예외가 발생한다")
    void commentDeleteFailByNoAuth() {
        Long postId = 1L;
        Long writerId = 1L;
        Long loginUserId = 2L;
        Long commentId = 10L;
        User writer = createUser(writerId);
        Post post = createPost(postId, writer);
        Comment comment = createComment(commentId, writer, post);
        CommentDeleteRequestDto request = createCommentDeleteRequest(commentId);

        when(postRepository.findById(postId))
                .thenReturn(Optional.of(post));
        when(commentRepository.findById(commentId))
                .thenReturn(Optional.of(comment));

        assertThatThrownBy(() -> commentService.commentDelete(postId, loginUserId, request))
                .isInstanceOf(AuthException.class)
                .hasMessage("No_Auth");

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
