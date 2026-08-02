package kr.adapterz.springdatajpa.service;

import kr.adapterz.springdatajpa.auth.CustomUserDetails;
import kr.adapterz.springdatajpa.auth.JwtProvider;
import kr.adapterz.springdatajpa.auth.RefreshTokenProvider;
import kr.adapterz.springdatajpa.dto.user.SessionRefreshResponseDto;
import kr.adapterz.springdatajpa.dto.user.SessionRequestDto;
import kr.adapterz.springdatajpa.dto.user.SessionResponseDto;
import kr.adapterz.springdatajpa.entity.AuthSession;
import kr.adapterz.springdatajpa.entity.User;
import kr.adapterz.springdatajpa.exception.AuthException;
import kr.adapterz.springdatajpa.exception.LoginFailedException;
import kr.adapterz.springdatajpa.repository.AuthSessionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SessionServiceTest {

    @Mock
    private AuthenticationManager authenticationManager;

    @Mock
    private JwtProvider jwtProvider;

    @Mock
    private RefreshTokenProvider refreshTokenProvider;

    @Mock
    private AuthSessionRepository authSessionRepository;

    @Mock
    private Authentication authentication;

    @InjectMocks
    private SessionService sessionService;

    @Test
    void 로그인_인증_실패_시_Login_Failed_예외가_발생한다() {
        SessionRequestDto request = createSessionRequest("test@test.com", "Password1!");

        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                .thenThrow(new BadCredentialsException("Bad credentials"));

        assertThatThrownBy(() -> sessionService.createSession(request))
                .isInstanceOf(LoginFailedException.class)
                .hasMessage("Login_Failed");

        verify(authenticationManager).authenticate(any(UsernamePasswordAuthenticationToken.class));
        verify(jwtProvider, never()).createAccessToken(anyLong(), anyLong());
    }

    @Test
    void 정지된_계정으로_로그인하면_Suspended_Account_예외가_발생한다() {
        SessionRequestDto request = createSessionRequest("suspended@test.com", "Password1!");

        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                .thenThrow(new DisabledException("disabled"));

        assertThatThrownBy(() -> sessionService.createSession(request))
                .isInstanceOf(LoginFailedException.class)
                .hasMessage("Suspended_Account");

        verify(authenticationManager).authenticate(any(UsernamePasswordAuthenticationToken.class));
        verify(jwtProvider, never()).createAccessToken(anyLong(), anyLong());
    }

    @Test
    void 로그인_성공_시_AuthenticationManager로_인증한_뒤_JWT를_발급한다() {
        SessionRequestDto request = createSessionRequest("test@test.com", "Password1!");

        User user = createUser(1L, "test@test.com", "encoded-password");
        CustomUserDetails userDetails = new CustomUserDetails(user);

        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                .thenReturn(authentication);

        when(authentication.getPrincipal())
                .thenReturn(userDetails);

        when(jwtProvider.createAccessToken(1L, 0L))
                .thenReturn("access-token");

        when(refreshTokenProvider.createRefreshToken())
                .thenReturn("refresh-token");

        when(refreshTokenProvider.hashRefreshToken("refresh-token"))
                .thenReturn("refresh-token-hash");

        when(refreshTokenProvider.createExpirationTime())
                .thenReturn(LocalDateTime.of(2026, 7, 21, 3, 0));

        SessionResponseDto response = sessionService.createSession(request);

        assertThat(response.getMessage()).isEqualTo("login_success");
        assertThat(response.getAccessToken()).isEqualTo("access-token");
        assertThat(response.getRefreshToken()).isEqualTo("refresh-token");
        assertThat(response.getUserId()).isEqualTo(1L);

        ArgumentCaptor<UsernamePasswordAuthenticationToken> tokenCaptor =
                ArgumentCaptor.forClass(UsernamePasswordAuthenticationToken.class);

        verify(authenticationManager).authenticate(tokenCaptor.capture());

        UsernamePasswordAuthenticationToken token = tokenCaptor.getValue();

        assertThat(token.getPrincipal()).isEqualTo("test@test.com");
        assertThat(token.getCredentials()).isEqualTo("Password1!");

        ArgumentCaptor<AuthSession> authSessionCaptor =
                ArgumentCaptor.forClass(AuthSession.class);

        verify(jwtProvider).createAccessToken(1L, 0L);
        verify(authSessionRepository).save(authSessionCaptor.capture());

        AuthSession savedAuthSession = authSessionCaptor.getValue();

        assertThat(savedAuthSession.getUser()).isEqualTo(user);
        assertThat(savedAuthSession.getRefreshTokenHash())
                .isEqualTo("refresh-token-hash");
        assertThat(savedAuthSession.getRefreshExpiresAt())
                .isEqualTo(LocalDateTime.of(2026, 7, 21, 3, 0));
    }

    @Test
    void 리프레시_토큰이_유효하면_새_액세스_토큰과_새_리프레시_토큰을_발급하고_세션을_회전한다() {
        User user = createUser(1L, "test@test.com", "encoded-password");
        AuthSession authSession = new AuthSession(
                user,
                "old-refresh-token-hash",
                LocalDateTime.now().plusHours(1)
        );

        when(refreshTokenProvider.hashRefreshToken("old-refresh-token"))
                .thenReturn("old-refresh-token-hash");

        when(authSessionRepository.findByRefreshTokenHash("old-refresh-token-hash"))
                .thenReturn(Optional.of(authSession));

        when(refreshTokenProvider.createRefreshToken())
                .thenReturn("new-refresh-token");

        when(refreshTokenProvider.hashRefreshToken("new-refresh-token"))
                .thenReturn("new-refresh-token-hash");

        when(refreshTokenProvider.createExpirationTime())
                .thenReturn(LocalDateTime.of(2026, 7, 21, 6, 0));

        when(jwtProvider.createAccessToken(1L, 0L))
                .thenReturn("new-access-token");

        SessionRefreshResponseDto response =
                sessionService.refreshSession("old-refresh-token");

        assertThat(response.getMessage()).isEqualTo("refresh_success");
        assertThat(response.getAccessToken()).isEqualTo("new-access-token");
        assertThat(response.getRefreshToken()).isEqualTo("new-refresh-token");
        assertThat(authSession.getRefreshTokenHash())
                .isEqualTo("new-refresh-token-hash");
        assertThat(authSession.getRefreshExpiresAt())
                .isEqualTo(LocalDateTime.of(2026, 7, 21, 6, 0));

        verify(jwtProvider).createAccessToken(1L, 0L);
    }

    @Test
    void 리프레시_토큰이_없으면_Invalid_Refresh_Token_예외가_발생한다() {
        assertThatThrownBy(() -> sessionService.refreshSession(" "))
                .isInstanceOf(AuthException.class)
                .hasMessage("Invalid_Refresh_Token");

        verifyNoInteractions(authSessionRepository);
        verify(jwtProvider, never()).createAccessToken(anyLong(), anyLong());
    }

    @Test
    void 저장된_세션이_없으면_Invalid_Refresh_Token_예외가_발생한다() {
        when(refreshTokenProvider.hashRefreshToken("unknown-refresh-token"))
                .thenReturn("unknown-refresh-token-hash");

        when(authSessionRepository.findByRefreshTokenHash("unknown-refresh-token-hash"))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> sessionService.refreshSession("unknown-refresh-token"))
                .isInstanceOf(AuthException.class)
                .hasMessage("Invalid_Refresh_Token");

        verify(jwtProvider, never()).createAccessToken(anyLong(), anyLong());
    }

    @Test
    void 폐기된_세션이면_Invalid_Refresh_Token_예외가_발생한다() {
        AuthSession authSession = new AuthSession(
                createUser(1L, "test@test.com", "encoded-password"),
                "refresh-token-hash",
                LocalDateTime.now().plusHours(1)
        );
        authSession.revoke(LocalDateTime.now());

        when(refreshTokenProvider.hashRefreshToken("refresh-token"))
                .thenReturn("refresh-token-hash");

        when(authSessionRepository.findByRefreshTokenHash("refresh-token-hash"))
                .thenReturn(Optional.of(authSession));

        assertThatThrownBy(() -> sessionService.refreshSession("refresh-token"))
                .isInstanceOf(AuthException.class)
                .hasMessage("Invalid_Refresh_Token");

        verify(jwtProvider, never()).createAccessToken(anyLong(), anyLong());
    }

    @Test
    void 만료된_세션이면_Invalid_Refresh_Token_예외가_발생한다() {
        AuthSession authSession = new AuthSession(
                createUser(1L, "test@test.com", "encoded-password"),
                "refresh-token-hash",
                LocalDateTime.now().minusSeconds(1)
        );

        when(refreshTokenProvider.hashRefreshToken("refresh-token"))
                .thenReturn("refresh-token-hash");

        when(authSessionRepository.findByRefreshTokenHash("refresh-token-hash"))
                .thenReturn(Optional.of(authSession));

        assertThatThrownBy(() -> sessionService.refreshSession("refresh-token"))
                .isInstanceOf(AuthException.class)
                .hasMessage("Invalid_Refresh_Token");

        verify(jwtProvider, never()).createAccessToken(anyLong(), anyLong());
    }

    @Test
    void 탈퇴한_유저의_세션이면_Invalid_Refresh_Token_예외가_발생한다() {
        User user = createUser(1L, "test@test.com", "encoded-password");
        user.delete();
        AuthSession authSession = new AuthSession(
                user,
                "refresh-token-hash",
                LocalDateTime.now().plusHours(1)
        );

        when(refreshTokenProvider.hashRefreshToken("refresh-token"))
                .thenReturn("refresh-token-hash");

        when(authSessionRepository.findByRefreshTokenHash("refresh-token-hash"))
                .thenReturn(Optional.of(authSession));

        assertThatThrownBy(() -> sessionService.refreshSession("refresh-token"))
                .isInstanceOf(AuthException.class)
                .hasMessage("Invalid_Refresh_Token");

        verify(jwtProvider, never()).createAccessToken(anyLong(), anyLong());
    }

    @Test
    void 로그아웃_시_저장된_리프레시_토큰_세션을_폐기한다() {
        AuthSession authSession = new AuthSession(
                createUser(1L, "test@test.com", "encoded-password"),
                "refresh-token-hash",
                LocalDateTime.now().plusHours(1)
        );

        when(refreshTokenProvider.hashRefreshToken("refresh-token"))
                .thenReturn("refresh-token-hash");

        when(authSessionRepository.findByRefreshTokenHash("refresh-token-hash"))
                .thenReturn(Optional.of(authSession));

        sessionService.deleteSession("refresh-token");

        assertThat(authSession.getRevokedAt()).isNotNull();
    }

    @Test
    void 로그아웃_시_리프레시_토큰이_비어_있으면_아무_작업도_하지_않는다() {
        sessionService.deleteSession(" ");

        verifyNoInteractions(refreshTokenProvider);
        verifyNoInteractions(authSessionRepository);
    }

    private SessionRequestDto createSessionRequest(String email, String password) {
        SessionRequestDto request = new SessionRequestDto();
        ReflectionTestUtils.setField(request, "email", email);
        ReflectionTestUtils.setField(request, "password", password);
        return request;
    }

    private User createUser(Long userId, String email, String encodedPassword) {
        User user = new User(email, encodedPassword, "tester", "profile.png",0);
        ReflectionTestUtils.setField(user, "userId", userId);
        return user;
    }
}
