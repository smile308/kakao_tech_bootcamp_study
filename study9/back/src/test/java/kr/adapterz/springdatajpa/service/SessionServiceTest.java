package kr.adapterz.springdatajpa.service;

import kr.adapterz.springdatajpa.auth.CustomUserDetails;
import kr.adapterz.springdatajpa.auth.JwtProvider;
import kr.adapterz.springdatajpa.dto.user.SessionRequestDto;
import kr.adapterz.springdatajpa.dto.user.SessionResponseDto;
import kr.adapterz.springdatajpa.entity.User;
import kr.adapterz.springdatajpa.exception.LoginFailedException;
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

        // when
        SessionResponseDto response = sessionService.createSession(request);

        // then
        assertThat(response.getMessage()).isEqualTo("login_success");
        assertThat(response.getAccessToken()).isEqualTo("access-token");
        assertThat(response.getUserId()).isEqualTo(1L);

        ArgumentCaptor<UsernamePasswordAuthenticationToken> tokenCaptor =
                ArgumentCaptor.forClass(UsernamePasswordAuthenticationToken.class);

        verify(authenticationManager).authenticate(tokenCaptor.capture());

        UsernamePasswordAuthenticationToken token = tokenCaptor.getValue();

        assertThat(token.getPrincipal()).isEqualTo("test@test.com");
        assertThat(token.getCredentials()).isEqualTo("Password1!");

        verify(jwtProvider).createAccessToken(1L);
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
