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
import org.junit.jupiter.api.DisplayName;
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
    @DisplayName("로그인 인증 실패 시 Login_Failed 예외가 발생한다")
    void createSessionFailByAuthenticationFail() {
        // given
        SessionRequestDto request = createSessionRequest("test@test.com", "Password1!");

        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                .thenThrow(new BadCredentialsException("Bad credentials"));

        // when & then
        assertThatThrownBy(() -> sessionService.createSession(request))
                .isInstanceOf(LoginFailedException.class)
                .hasMessage("Login_Failed");

        verify(authenticationManager).authenticate(any(UsernamePasswordAuthenticationToken.class));
        verify(jwtProvider, never()).createAccessToken(anyLong());
    }

    @Test
    @DisplayName("정지된 계정으로 로그인하면 Suspended_Account 예외가 발생한다")
    void createSessionFailBySuspendedAccount() {
        // given
        SessionRequestDto request = createSessionRequest("suspended@test.com", "Password1!");

        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                .thenThrow(new DisabledException("disabled"));

        // when & then
        assertThatThrownBy(() -> sessionService.createSession(request))
                .isInstanceOf(LoginFailedException.class)
                .hasMessage("Suspended_Account");

        verify(authenticationManager).authenticate(any(UsernamePasswordAuthenticationToken.class));
        verify(jwtProvider, never()).createAccessToken(anyLong());
    }

    @Test
    @DisplayName("로그인 성공 시 AuthenticationManager로 인증한 뒤 JWT를 발급한다")
    void createSessionSuccess() {
        // given
        SessionRequestDto request = createSessionRequest("test@test.com", "Password1!");

        User user = createUser(1L, "test@test.com", "encoded-password");
        CustomUserDetails userDetails = new CustomUserDetails(user);

        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                .thenReturn(authentication);

        when(authentication.getPrincipal())
                .thenReturn(userDetails);

        when(jwtProvider.createAccessToken(1L))
                .thenReturn("access-token");

        when(refreshTokenProvider.createRefreshToken())
                .thenReturn("refresh-token");

        when(refreshTokenProvider.hashRefreshToken("refresh-token"))
                .thenReturn("refresh-token-hash");

        when(refreshTokenProvider.createExpirationTime())
                .thenReturn(LocalDateTime.of(2026, 7, 21, 3, 0));

        // when
        SessionResponseDto response = sessionService.createSession(request);

        // then
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

        verify(jwtProvider).createAccessToken(1L);
        verify(authSessionRepository).save(authSessionCaptor.capture());

        AuthSession savedAuthSession = authSessionCaptor.getValue();

        assertThat(savedAuthSession.getUser()).isEqualTo(user);
        assertThat(savedAuthSession.getRefreshTokenHash())
                .isEqualTo("refresh-token-hash");
        assertThat(savedAuthSession.getRefreshExpiresAt())
                .isEqualTo(LocalDateTime.of(2026, 7, 21, 3, 0));
    }

    @Test
    @DisplayName("리프레시 토큰이 유효하면 새 액세스 토큰과 새 리프레시 토큰을 발급하고 세션을 회전한다")
    void refreshSessionSuccess() {
        // given
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

        when(jwtProvider.createAccessToken(1L))
                .thenReturn("new-access-token");

        // when
        SessionRefreshResponseDto response =
                sessionService.refreshSession("old-refresh-token");

        // then
        assertThat(response.getMessage()).isEqualTo("refresh_success");
        assertThat(response.getAccessToken()).isEqualTo("new-access-token");
        assertThat(response.getRefreshToken()).isEqualTo("new-refresh-token");
        assertThat(authSession.getRefreshTokenHash())
                .isEqualTo("new-refresh-token-hash");
        assertThat(authSession.getRefreshExpiresAt())
                .isEqualTo(LocalDateTime.of(2026, 7, 21, 6, 0));

        verify(jwtProvider).createAccessToken(1L);
    }

    @Test
    @DisplayName("리프레시 토큰이 없으면 Invalid_Refresh_Token 예외가 발생한다")
    void refreshSessionFailByBlankToken() {
        assertThatThrownBy(() -> sessionService.refreshSession(" "))
                .isInstanceOf(AuthException.class)
                .hasMessage("Invalid_Refresh_Token");

        verifyNoInteractions(authSessionRepository);
        verify(jwtProvider, never()).createAccessToken(anyLong());
    }

    @Test
    @DisplayName("저장된 세션이 없으면 Invalid_Refresh_Token 예외가 발생한다")
    void refreshSessionFailByUnknownToken() {
        // given
        when(refreshTokenProvider.hashRefreshToken("unknown-refresh-token"))
                .thenReturn("unknown-refresh-token-hash");

        when(authSessionRepository.findByRefreshTokenHash("unknown-refresh-token-hash"))
                .thenReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> sessionService.refreshSession("unknown-refresh-token"))
                .isInstanceOf(AuthException.class)
                .hasMessage("Invalid_Refresh_Token");

        verify(jwtProvider, never()).createAccessToken(anyLong());
    }

    @Test
    @DisplayName("폐기된 세션이면 Invalid_Refresh_Token 예외가 발생한다")
    void refreshSessionFailByRevokedSession() {
        // given
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

        // when & then
        assertThatThrownBy(() -> sessionService.refreshSession("refresh-token"))
                .isInstanceOf(AuthException.class)
                .hasMessage("Invalid_Refresh_Token");

        verify(jwtProvider, never()).createAccessToken(anyLong());
    }

    @Test
    @DisplayName("만료된 세션이면 Invalid_Refresh_Token 예외가 발생한다")
    void refreshSessionFailByExpiredSession() {
        // given
        AuthSession authSession = new AuthSession(
                createUser(1L, "test@test.com", "encoded-password"),
                "refresh-token-hash",
                LocalDateTime.now().minusSeconds(1)
        );

        when(refreshTokenProvider.hashRefreshToken("refresh-token"))
                .thenReturn("refresh-token-hash");

        when(authSessionRepository.findByRefreshTokenHash("refresh-token-hash"))
                .thenReturn(Optional.of(authSession));

        // when & then
        assertThatThrownBy(() -> sessionService.refreshSession("refresh-token"))
                .isInstanceOf(AuthException.class)
                .hasMessage("Invalid_Refresh_Token");

        verify(jwtProvider, never()).createAccessToken(anyLong());
    }

    @Test
    @DisplayName("탈퇴한 유저의 세션이면 Invalid_Refresh_Token 예외가 발생한다")
    void refreshSessionFailByDeletedUser() {
        // given
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

        // when & then
        assertThatThrownBy(() -> sessionService.refreshSession("refresh-token"))
                .isInstanceOf(AuthException.class)
                .hasMessage("Invalid_Refresh_Token");

        verify(jwtProvider, never()).createAccessToken(anyLong());
    }

    @Test
    @DisplayName("로그아웃 시 저장된 리프레시 토큰 세션을 폐기한다")
    void deleteSessionRevokesSession() {
        // given
        AuthSession authSession = new AuthSession(
                createUser(1L, "test@test.com", "encoded-password"),
                "refresh-token-hash",
                LocalDateTime.now().plusHours(1)
        );

        when(refreshTokenProvider.hashRefreshToken("refresh-token"))
                .thenReturn("refresh-token-hash");

        when(authSessionRepository.findByRefreshTokenHash("refresh-token-hash"))
                .thenReturn(Optional.of(authSession));

        // when
        sessionService.deleteSession("refresh-token");

        // then
        assertThat(authSession.getRevokedAt()).isNotNull();
    }

    @Test
    @DisplayName("로그아웃 시 리프레시 토큰이 비어 있으면 아무 작업도 하지 않는다")
    void deleteSessionDoesNothingByBlankToken() {
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
