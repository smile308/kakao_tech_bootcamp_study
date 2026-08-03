package kr.adapterz.springdatajpa.controller;

import jakarta.servlet.http.HttpServletResponse;
import kr.adapterz.springdatajpa.auth.CustomUserDetails;
import kr.adapterz.springdatajpa.auth.RefreshCookieProvider;
import kr.adapterz.springdatajpa.dto.comment.CommentDeleteRequestDto;
import kr.adapterz.springdatajpa.dto.comment.CommentDeleteResponseDto;
import kr.adapterz.springdatajpa.dto.comment.CommentFixRequestDto;
import kr.adapterz.springdatajpa.dto.comment.CommentPostRequestDto;
import kr.adapterz.springdatajpa.dto.comment.CommentResponseDto;
import kr.adapterz.springdatajpa.dto.post.LikeCancelResponseDto;
import kr.adapterz.springdatajpa.dto.post.LikeResponseDto;
import kr.adapterz.springdatajpa.dto.post.PostDeleteRequestDto;
import kr.adapterz.springdatajpa.dto.post.PostDeleteResponseDto;
import kr.adapterz.springdatajpa.dto.post.PostFixRequestDto;
import kr.adapterz.springdatajpa.dto.post.PostFixResponseDto;
import kr.adapterz.springdatajpa.dto.post.PostPageResponseDto;
import kr.adapterz.springdatajpa.dto.post.PostReportResponseDto;
import kr.adapterz.springdatajpa.dto.post.PostRequestDto;
import kr.adapterz.springdatajpa.dto.post.PostResponseDto;
import kr.adapterz.springdatajpa.dto.post.PostViewResponseDto;
import kr.adapterz.springdatajpa.dto.user.SessionRefreshResponseDto;
import kr.adapterz.springdatajpa.dto.user.SessionRequestDto;
import kr.adapterz.springdatajpa.dto.user.SessionResponseDto;
import kr.adapterz.springdatajpa.dto.user.UserDeleteResponseDto;
import kr.adapterz.springdatajpa.dto.user.UserInfoResponseDto;
import kr.adapterz.springdatajpa.dto.user.UserPasswordRequestDto;
import kr.adapterz.springdatajpa.dto.user.UserPasswordResponseDto;
import kr.adapterz.springdatajpa.dto.user.UserPatchRequestDto;
import kr.adapterz.springdatajpa.dto.user.UserPatchResponseDto;
import kr.adapterz.springdatajpa.dto.user.UserRequestDto;
import kr.adapterz.springdatajpa.dto.user.UserResponseDto;
import kr.adapterz.springdatajpa.service.CommentService;
import kr.adapterz.springdatajpa.service.PostService;
import kr.adapterz.springdatajpa.service.SessionService;
import kr.adapterz.springdatajpa.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ControllerDelegationTest {

    private static final Long USER_ID = 42L;
    private static final Long POST_ID = 7L;

    @Mock
    private PostService postService;

    @Mock
    private CommentService commentService;

    @Mock
    private SessionService sessionService;

    @Mock
    private UserService userService;

    @Mock
    private RefreshCookieProvider refreshCookieProvider;

    @Mock
    private CustomUserDetails userDetails;

    @Mock
    private HttpServletResponse servletResponse;

    @BeforeEach
    void setUp() {
        org.mockito.Mockito.lenient()
                .when(userDetails.getUserId())
                .thenReturn(USER_ID);
    }

    @Test
    void PostController는_인증_사용자와_요청값을_Service에_전달한다() {
        PostController controller = new PostController(postService);
        PostRequestDto createRequest = org.mockito.Mockito.mock(PostRequestDto.class);
        PostFixRequestDto fixRequest = org.mockito.Mockito.mock(PostFixRequestDto.class);
        PostDeleteRequestDto deleteRequest = org.mockito.Mockito.mock(PostDeleteRequestDto.class);
        PostPageResponseDto pageResponse = org.mockito.Mockito.mock(PostPageResponseDto.class);
        PostResponseDto createResponse = org.mockito.Mockito.mock(PostResponseDto.class);
        PostViewResponseDto viewResponse = org.mockito.Mockito.mock(PostViewResponseDto.class);
        PostFixResponseDto fixResponse = org.mockito.Mockito.mock(PostFixResponseDto.class);
        PostDeleteResponseDto deleteResponse = org.mockito.Mockito.mock(PostDeleteResponseDto.class);
        LikeResponseDto likeResponse = org.mockito.Mockito.mock(LikeResponseDto.class);
        LikeCancelResponseDto cancelResponse = org.mockito.Mockito.mock(LikeCancelResponseDto.class);
        PostReportResponseDto reportResponse = org.mockito.Mockito.mock(PostReportResponseDto.class);

        when(postService.getPostList(2, 10)).thenReturn(pageResponse);
        when(postService.createPost(USER_ID, createRequest)).thenReturn(createResponse);
        when(postService.getPostView(POST_ID, USER_ID)).thenReturn(viewResponse);
        when(postService.fixPost(POST_ID, USER_ID, fixRequest)).thenReturn(fixResponse);
        when(postService.deletePost(POST_ID, USER_ID, deleteRequest)).thenReturn(deleteResponse);
        when(postService.likePost(POST_ID, USER_ID)).thenReturn(likeResponse);
        when(postService.cancelLike(POST_ID, USER_ID)).thenReturn(cancelResponse);
        when(postService.reportPost(POST_ID, USER_ID)).thenReturn(reportResponse);

        assertThat(controller.getPostList(2, 10)).isSameAs(pageResponse);
        assertThat(controller.createPost(userDetails, createRequest)).isSameAs(createResponse);
        assertThat(controller.getPostView(POST_ID, userDetails)).isSameAs(viewResponse);
        assertThat(controller.fixPost(POST_ID, userDetails, fixRequest)).isSameAs(fixResponse);
        assertThat(controller.deletePost(POST_ID, userDetails, deleteRequest)).isSameAs(deleteResponse);
        assertThat(controller.likePost(POST_ID, userDetails)).isSameAs(likeResponse);
        assertThat(controller.cancelLike(POST_ID, userDetails)).isSameAs(cancelResponse);
        assertThat(controller.reportPost(POST_ID, userDetails)).isSameAs(reportResponse);
    }

    @Test
    void CommentController는_게시글과_사용자와_요청값을_Service에_전달한다() {
        CommentController controller = new CommentController(commentService);
        CommentPostRequestDto postRequest = org.mockito.Mockito.mock(CommentPostRequestDto.class);
        CommentDeleteRequestDto deleteRequest = org.mockito.Mockito.mock(CommentDeleteRequestDto.class);
        CommentFixRequestDto fixRequest = org.mockito.Mockito.mock(CommentFixRequestDto.class);
        CommentResponseDto postResponse = org.mockito.Mockito.mock(CommentResponseDto.class);
        CommentDeleteResponseDto deleteResponse = org.mockito.Mockito.mock(CommentDeleteResponseDto.class);
        CommentResponseDto fixResponse = org.mockito.Mockito.mock(CommentResponseDto.class);

        when(commentService.commentPost(POST_ID, USER_ID, postRequest)).thenReturn(postResponse);
        when(commentService.commentDelete(POST_ID, USER_ID, deleteRequest)).thenReturn(deleteResponse);
        when(commentService.commentFix(POST_ID, USER_ID, fixRequest)).thenReturn(fixResponse);

        assertThat(controller.commentPost(POST_ID, userDetails, postRequest)).isSameAs(postResponse);
        assertThat(controller.commentDelete(POST_ID, userDetails, deleteRequest)).isSameAs(deleteResponse);
        assertThat(controller.commentFix(POST_ID, userDetails, fixRequest)).isSameAs(fixResponse);
    }

    @Test
    void SessionController는_refresh_Cookie를_발급하고_만료시킨다() {
        SessionController controller =
                new SessionController(sessionService, refreshCookieProvider);
        SessionRequestDto request = org.mockito.Mockito.mock(SessionRequestDto.class);
        SessionResponseDto createResponse = org.mockito.Mockito.mock(SessionResponseDto.class);
        SessionRefreshResponseDto refreshResponse =
                org.mockito.Mockito.mock(SessionRefreshResponseDto.class);
        ResponseCookie createdCookie =
                ResponseCookie.from(RefreshCookieProvider.COOKIE_NAME, "created").build();
        ResponseCookie rotatedCookie =
                ResponseCookie.from(RefreshCookieProvider.COOKIE_NAME, "rotated").build();
        ResponseCookie expiredCookie =
                ResponseCookie.from(RefreshCookieProvider.COOKIE_NAME, "").maxAge(0).build();

        when(sessionService.createSession(request)).thenReturn(createResponse);
        when(createResponse.getRefreshToken()).thenReturn("created");
        when(refreshCookieProvider.createRefreshTokenCookie("created"))
                .thenReturn(createdCookie);
        when(sessionService.refreshSession("old-token")).thenReturn(refreshResponse);
        when(refreshResponse.getRefreshToken()).thenReturn("rotated");
        when(refreshCookieProvider.createRefreshTokenCookie("rotated"))
                .thenReturn(rotatedCookie);
        when(refreshCookieProvider.createExpiredRefreshTokenCookie())
                .thenReturn(expiredCookie);

        assertThat(controller.createSession(request, servletResponse))
                .isSameAs(createResponse);
        assertThat(controller.refreshSession("old-token", servletResponse))
                .isSameAs(refreshResponse);
        controller.deleteSession("rotated", servletResponse);

        verify(sessionService).deleteSession("rotated");
        verify(servletResponse).addHeader(
                HttpHeaders.SET_COOKIE,
                createdCookie.toString()
        );
        verify(servletResponse).addHeader(
                HttpHeaders.SET_COOKIE,
                rotatedCookie.toString()
        );
        verify(servletResponse).addHeader(
                HttpHeaders.SET_COOKIE,
                expiredCookie.toString()
        );
    }

    @Test
    void UserController는_사용자_작업을_전달하고_인증변경시_Cookie를_만료시킨다() {
        UserController controller =
                new UserController(userService, refreshCookieProvider);
        UserRequestDto createRequest = org.mockito.Mockito.mock(UserRequestDto.class);
        UserPatchRequestDto patchRequest = org.mockito.Mockito.mock(UserPatchRequestDto.class);
        UserPasswordRequestDto passwordRequest =
                org.mockito.Mockito.mock(UserPasswordRequestDto.class);
        UserResponseDto createResponse = org.mockito.Mockito.mock(UserResponseDto.class);
        UserInfoResponseDto infoResponse = org.mockito.Mockito.mock(UserInfoResponseDto.class);
        UserDeleteResponseDto deleteResponse =
                org.mockito.Mockito.mock(UserDeleteResponseDto.class);
        UserPatchResponseDto patchResponse =
                org.mockito.Mockito.mock(UserPatchResponseDto.class);
        UserPasswordResponseDto passwordResponse =
                org.mockito.Mockito.mock(UserPasswordResponseDto.class);
        ResponseCookie expiredCookie =
                ResponseCookie.from(RefreshCookieProvider.COOKIE_NAME, "").maxAge(0).build();

        when(userService.createUser(createRequest)).thenReturn(createResponse);
        when(userService.getMyInfo(USER_ID)).thenReturn(infoResponse);
        when(userService.deleteUser(USER_ID)).thenReturn(deleteResponse);
        when(userService.patchUser(USER_ID, patchRequest)).thenReturn(patchResponse);
        when(userService.setPassword(USER_ID, passwordRequest)).thenReturn(passwordResponse);
        when(refreshCookieProvider.createExpiredRefreshTokenCookie())
                .thenReturn(expiredCookie);

        assertThat(controller.createUser(createRequest)).isSameAs(createResponse);
        assertThat(controller.getMyInfo(userDetails)).isSameAs(infoResponse);
        assertThat(controller.deleteUser(userDetails, servletResponse))
                .isSameAs(deleteResponse);
        assertThat(controller.patchUser(userDetails, patchRequest))
                .isSameAs(patchResponse);
        assertThat(controller.setPassword(
                userDetails,
                passwordRequest,
                servletResponse
        )).isSameAs(passwordResponse);

        verify(servletResponse, org.mockito.Mockito.times(2)).addHeader(
                HttpHeaders.SET_COOKIE,
                expiredCookie.toString()
        );
    }
}
