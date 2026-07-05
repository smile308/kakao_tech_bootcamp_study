package kr.adapterz.springdatajpa.service;

import kr.adapterz.springdatajpa.auth.JwtProvider;
import kr.adapterz.springdatajpa.dto.user.UserPatchRequestDto;
import kr.adapterz.springdatajpa.dto.user.UserRequestDto;
import kr.adapterz.springdatajpa.entity.User;
import kr.adapterz.springdatajpa.exception.InvalidRequestException;
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
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private JwtProvider jwtProvider;

    @InjectMocks
    private UserService userService;

    @Test
    @DisplayName("회원가입 시 비밀번호와 비밀번호 확인이 다르면 Invalid_Password 예외가 발생한다")
    void createUserFailByPasswordMismatch() {
        // given
        UserRequestDto request = createUserRequest(
                "test@test.com",
                "Password1!",
                "WrongPassword1!",
                "tester",
                "profile.png"
        );

        // when & then
        assertThatThrownBy(() -> userService.createUser(request))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessage("Invalid_Password");

        verify(userRepository, never()).existsByEmailAndDeletedFalse(anyString());
        verify(userRepository, never()).existsByNicknameAndDeletedFalse(anyString());
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    @DisplayName("회원가입 시 이미 존재하는 이메일이면 Existed_Email 예외가 발생한다")
    void createUserFailByDuplicatedEmail() {
        // given
        UserRequestDto request = createUserRequest(
                "test@test.com",
                "Password1!",
                "Password1!",
                "tester",
                "profile.png"
        );

        when(userRepository.existsByEmailAndDeletedFalse("test@test.com"))
                .thenReturn(true);

        // when & then
        assertThatThrownBy(() -> userService.createUser(request))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessage("Existed_Email");

        verify(userRepository).existsByEmailAndDeletedFalse("test@test.com");
        verify(userRepository, never()).existsByNicknameAndDeletedFalse(anyString());
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    @DisplayName("회원가입 시 이미 존재하는 닉네임이면 Existed_Nickname 예외가 발생한다")
    void createUserFailByDuplicatedNickname() {
        // given
        UserRequestDto request = createUserRequest(
                "test@test.com",
                "Password1!",
                "Password1!",
                "tester",
                "profile.png"
        );

        when(userRepository.existsByEmailAndDeletedFalse("test@test.com"))
                .thenReturn(false);
        when(userRepository.existsByNicknameAndDeletedFalse("tester"))
                .thenReturn(true);

        // when & then
        assertThatThrownBy(() -> userService.createUser(request))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessage("Existed_Nickname");

        verify(userRepository).existsByEmailAndDeletedFalse("test@test.com");
        verify(userRepository).existsByNicknameAndDeletedFalse("tester");
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    @DisplayName("회원가입 시 이메일과 닉네임이 중복되지 않으면 유저가 저장된다")
    void createUserSuccess() {
        // given
        UserRequestDto request = createUserRequest(
                "test@test.com",
                "Password1!",
                "Password1!",
                "tester",
                "profile.png"
        );

        User savedUser = new User(
                "test@test.com",
                "Password1!",
                "tester",
                "profile.png"
        );

        when(userRepository.existsByEmailAndDeletedFalse("test@test.com"))
                .thenReturn(false);
        when(userRepository.existsByNicknameAndDeletedFalse("tester"))
                .thenReturn(false);
        when(userRepository.save(any(User.class)))
                .thenReturn(savedUser);

        // when
        userService.createUser(request);

        // then
        verify(userRepository).existsByEmailAndDeletedFalse("test@test.com");
        verify(userRepository).existsByNicknameAndDeletedFalse("tester");
        verify(userRepository).save(any(User.class));
    }

    @Test
    @DisplayName("회원정보 수정 시 다른 사용자가 이미 쓰는 닉네임이면 Existed_Nickname 예외가 발생한다")
    void patchUserFailByDuplicatedNickname() {
        // given
        String authorizationHeader = "Bearer token";
        Long loginUserId = 1L;

        User loginUser = new User(
                "test@test.com",
                "Password1!",
                "tester",
                "profile.png"
        );

        ReflectionTestUtils.setField(loginUser, "userId", loginUserId);

        UserPatchRequestDto request = createUserPatchRequest(
                "duplicatedNickname",
                "new-profile.png"
        );

        when(jwtProvider.getUserIdFromAuthorizationHeader(authorizationHeader))
                .thenReturn(loginUserId);
        when(userRepository.findByUserIdAndDeletedFalse(loginUserId))
                .thenReturn(Optional.of(loginUser));
        when(userRepository.existsByNicknameAndDeletedFalseAndUserIdNot("duplicatedNickname", loginUserId))
                .thenReturn(true);

        // when & then
        assertThatThrownBy(() -> userService.patchUser(authorizationHeader, request))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessage("Existed_Nickname");

        verify(jwtProvider).getUserIdFromAuthorizationHeader(authorizationHeader);
        verify(userRepository).findByUserIdAndDeletedFalse(loginUserId);
        verify(userRepository).existsByNicknameAndDeletedFalseAndUserIdNot("duplicatedNickname", loginUserId);
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    @DisplayName("회원정보 수정 시 내 userId를 제외하고 닉네임 중복이 없으면 수정된다")
    void patchUserSuccess() {
        // given
        String authorizationHeader = "Bearer token";
        Long loginUserId = 1L;

        User loginUser = new User(
                "test@test.com",
                "Password1!",
                "oldNickname",
                "old-profile.png"
        );

        ReflectionTestUtils.setField(loginUser, "userId", loginUserId);

        UserPatchRequestDto request = createUserPatchRequest(
                "newNickname",
                "new-profile.png"
        );

        when(jwtProvider.getUserIdFromAuthorizationHeader(authorizationHeader))
                .thenReturn(loginUserId);
        when(userRepository.findByUserIdAndDeletedFalse(loginUserId))
                .thenReturn(Optional.of(loginUser));
        when(userRepository.existsByNicknameAndDeletedFalseAndUserIdNot("newNickname", loginUserId))
                .thenReturn(false);

        // when
        userService.patchUser(authorizationHeader, request);

        // then
        verify(jwtProvider).getUserIdFromAuthorizationHeader(authorizationHeader);
        verify(userRepository).findByUserIdAndDeletedFalse(loginUserId);
        verify(userRepository).existsByNicknameAndDeletedFalseAndUserIdNot("newNickname", loginUserId);

        org.assertj.core.api.Assertions.assertThat(loginUser.getNickname()).isEqualTo("newNickname");
        org.assertj.core.api.Assertions.assertThat(loginUser.getProfileImage()).isEqualTo("new-profile.png");
    }

    private UserRequestDto createUserRequest(
            String email,
            String password,
            String passwordCheck,
            String nickname,
            String profileImage
    ) {
        UserRequestDto request = new UserRequestDto();

        ReflectionTestUtils.setField(request, "email", email);
        ReflectionTestUtils.setField(request, "password", password);
        ReflectionTestUtils.setField(request, "passwordCheck", passwordCheck);
        ReflectionTestUtils.setField(request, "nickname", nickname);
        ReflectionTestUtils.setField(request, "profileImage", profileImage);

        return request;
    }

    private UserPatchRequestDto createUserPatchRequest(
            String nickname,
            String profileImage
    ) {
        UserPatchRequestDto request = new UserPatchRequestDto();

        ReflectionTestUtils.setField(request, "nickname", nickname);
        ReflectionTestUtils.setField(request, "profileImage", profileImage);

        return request;
    }
}