package kr.adapterz.springdatajpa.service;

import kr.adapterz.springdatajpa.dto.post.PostFixRequestDto;
import kr.adapterz.springdatajpa.entity.Like;
import kr.adapterz.springdatajpa.entity.Post;
import kr.adapterz.springdatajpa.entity.User;
import kr.adapterz.springdatajpa.exception.AuthException;
import kr.adapterz.springdatajpa.exception.DataNullException;
import kr.adapterz.springdatajpa.exception.InvalidRequestException;
import kr.adapterz.springdatajpa.repository.CommentRepository;
import kr.adapterz.springdatajpa.repository.LikeRepository;
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

    @Test
    @DisplayName("게시글 상세 조회 시 게시글이 없으면 No_Post 예외가 발생한다")
    void getPostViewFailByNoPost() {
        Long postId = 1L;

        when(postRepository.findByPostIdAndDeletedFalse(postId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> postService.getPostView(postId))
                .isInstanceOf(DataNullException.class)
                .hasMessage("No_Post");

        verify(postRepository).findByPostIdAndDeletedFalse(postId);
        verify(commentRepository, never()).findByPostWithUser(any(Post.class));
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
        User user = new User("test" + userId + "@test.com", "Password1!", "tester" + userId, "profile.png");
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
