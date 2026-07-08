package kr.adapterz.springdatajpa.service;

import kr.adapterz.springdatajpa.auth.JwtProvider;
import kr.adapterz.springdatajpa.dto.user.SessionRequestDto;
import kr.adapterz.springdatajpa.dto.user.SessionResponseDto;
import kr.adapterz.springdatajpa.entity.User;
import kr.adapterz.springdatajpa.exception.LoginFailedException;
import kr.adapterz.springdatajpa.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SessionServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private JwtProvider jwtProvider;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private SessionService sessionService;

    @Test
    @DisplayName("로그인 시 이메일에 맞는 유저가 없으면 Login_Failed 예외가 발생한다")
    void createSessionFailByNoUser() {
        SessionRequestDto request = createSessionRequest("test@test.com", "Password1!");

        when(userRepository.findByEmailAndDeletedFalse("test@test.com"))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> sessionService.createSession(request))
                .isInstanceOf(LoginFailedException.class)
                .hasMessage("Login_Failed");

        verify(passwordEncoder, never()).matches("Password1!", "encoded-password");
        verify(jwtProvider, never()).createAccessToken(1L);
    }

    @Test
    @DisplayName("로그인 시 비밀번호가 암호화된 비밀번호와 맞지 않으면 Login_Failed 예외가 발생한다")
    void createSessionFailByWrongPassword() {
        SessionRequestDto request = createSessionRequest("test@test.com", "Password1!");
        User user = createUser(1L, "encoded-password");

        when(userRepository.findByEmailAndDeletedFalse("test@test.com"))
                .thenReturn(Optional.of(user));
        when(passwordEncoder.matches("Password1!", "encoded-password"))
                .thenReturn(false);

        assertThatThrownBy(() -> sessionService.createSession(request))
                .isInstanceOf(LoginFailedException.class)
                .hasMessage("Login_Failed");

        verify(jwtProvider, never()).createAccessToken(1L);
    }

    @Test
    @DisplayName("로그인 성공 시 암호화된 비밀번호를 비교한 뒤 JWT를 발급한다")
    void createSessionSuccess() {
        SessionRequestDto request = createSessionRequest("test@test.com", "Password1!");
        User user = createUser(1L, "encoded-password");

        when(userRepository.findByEmailAndDeletedFalse("test@test.com"))
                .thenReturn(Optional.of(user));
        when(passwordEncoder.matches("Password1!", "encoded-password"))
                .thenReturn(true);
        when(jwtProvider.createAccessToken(1L))
                .thenReturn("access-token");

        SessionResponseDto response = sessionService.createSession(request);

        assertThat(response.getMessage()).isEqualTo("login_success");
        assertThat(response.getAccessToken()).isEqualTo("access-token");
        assertThat(response.getUserId()).isEqualTo(1L);
    }

    private SessionRequestDto createSessionRequest(String email, String password) {
        SessionRequestDto request = new SessionRequestDto();
        ReflectionTestUtils.setField(request, "email", email);
        ReflectionTestUtils.setField(request, "password", password);
        return request;
    }

    private User createUser(Long userId, String encodedPassword) {
        User user = new User("test@test.com", encodedPassword, "tester", "profile.png");
        ReflectionTestUtils.setField(user, "userId", userId);
        return user;
    }
}
