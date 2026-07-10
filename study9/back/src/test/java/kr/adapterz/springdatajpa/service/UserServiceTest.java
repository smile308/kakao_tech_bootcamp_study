package kr.adapterz.springdatajpa.service;

import kr.adapterz.springdatajpa.dto.user.UserPasswordRequestDto;
import kr.adapterz.springdatajpa.dto.user.UserPatchRequestDto;
import kr.adapterz.springdatajpa.dto.user.UserRequestDto;
import kr.adapterz.springdatajpa.dto.user.UserResponseDto;
import kr.adapterz.springdatajpa.entity.User;
import kr.adapterz.springdatajpa.exception.DataNullException;
import kr.adapterz.springdatajpa.exception.InvalidRequestException;
import kr.adapterz.springdatajpa.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

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
        verify(passwordEncoder, never()).encode(anyString());
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
        verify(passwordEncoder, never()).encode(anyString());
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
        verify(passwordEncoder, never()).encode(anyString());
    }

    @Test
    @DisplayName("회원가입 시 이메일과 닉네임이 중복되지 않으면 입력값과 암호화된 비밀번호로 유저가 저장된다")
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
                "encoded-password",
                "tester",
                "profile.png",
                0
        );

        when(userRepository.existsByEmailAndDeletedFalse("test@test.com"))
                .thenReturn(false);

        when(userRepository.existsByNicknameAndDeletedFalse("tester"))
                .thenReturn(false);

        when(passwordEncoder.encode("Password1!"))
                .thenReturn("encoded-password");

        when(userRepository.save(any(User.class)))
                .thenReturn(savedUser);

        // when
        userService.createUser(request);

        // then
        verify(userRepository).existsByEmailAndDeletedFalse("test@test.com");
        verify(userRepository).existsByNicknameAndDeletedFalse("tester");
        verify(passwordEncoder).encode("Password1!");

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());

        User capturedUser = userCaptor.getValue();

        assertThat(capturedUser.getEmail()).isEqualTo("test@test.com");
        assertThat(capturedUser.getNickname()).isEqualTo("tester");
        assertThat(capturedUser.getProfileImage()).isEqualTo("profile.png");

        assertThat(capturedUser.getPassword()).isEqualTo("encoded-password");
        assertThat(capturedUser.getPassword()).isNotEqualTo("Password1!");
    }

    @Test
    @DisplayName("회원정보 수정 시 다른 사용자가 이미 쓰는 닉네임이면 Existed_Nickname 예외가 발생한다")
    void patchUserFailByDuplicatedNickname() {
        // given
        Long loginUserId = 1L;

        User loginUser = new User(
                "test@test.com",
                "Password1!",
                "tester",
                "profile.png",
                0
        );

        ReflectionTestUtils.setField(loginUser, "userId", loginUserId);

        UserPatchRequestDto request = createUserPatchRequest(
                "duplicatedNickname",
                "new-profile.png"
        );

        when(userRepository.findByUserIdAndDeletedFalse(loginUserId))
                .thenReturn(Optional.of(loginUser));
        when(userRepository.existsByNicknameAndDeletedFalseAndUserIdNot("duplicatedNickname", loginUserId))
                .thenReturn(true);

        // when & then
        assertThatThrownBy(() -> userService.patchUser(loginUserId, request))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessage("Existed_Nickname");

        verify(userRepository).findByUserIdAndDeletedFalse(loginUserId);
        verify(userRepository).existsByNicknameAndDeletedFalseAndUserIdNot("duplicatedNickname", loginUserId);
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    @DisplayName("회원정보 수정 시 내 userId를 제외하고 닉네임 중복이 없으면 수정된다")
    void patchUserSuccess() {
        // given
        Long loginUserId = 1L;

        User loginUser = new User(
                "test@test.com",
                "Password1!",
                "oldNickname",
                "old-profile.png",
                0
        );

        ReflectionTestUtils.setField(loginUser, "userId", loginUserId);

        UserPatchRequestDto request = createUserPatchRequest(
                "newNickname",
                "new-profile.png"
        );

        when(userRepository.findByUserIdAndDeletedFalse(loginUserId))
                .thenReturn(Optional.of(loginUser));
        when(userRepository.existsByNicknameAndDeletedFalseAndUserIdNot("newNickname", loginUserId))
                .thenReturn(false);

        // when
        userService.patchUser(loginUserId, request);

        // then
        verify(userRepository).findByUserIdAndDeletedFalse(loginUserId);
        verify(userRepository).existsByNicknameAndDeletedFalseAndUserIdNot("newNickname", loginUserId);

        assertThat(loginUser.getNickname()).isEqualTo("newNickname");
        assertThat(loginUser.getProfileImage()).isEqualTo("new-profile.png");
    }

    @Test
    @DisplayName("내 정보 조회 시 로그인 유저가 없으면 No_User 예외가 발생한다")
    void getMyInfoFailByNoUser() {
        // given
        Long loginUserId = 1L;

        when(userRepository.findByUserIdAndDeletedFalse(loginUserId))
                .thenReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> userService.getMyInfo(loginUserId))
                .isInstanceOf(DataNullException.class)
                .hasMessage("No_User");

        verify(userRepository).findByUserIdAndDeletedFalse(loginUserId);
    }

    @Test
    @DisplayName("비밀번호 수정 시 비밀번호 확인이 다르면 Invalid_Password 예외가 발생한다")
    void setPasswordFailByPasswordMismatch() {
        // given
        Long loginUserId = 1L;
        UserPasswordRequestDto request = createUserPasswordRequest("Password1!", "WrongPassword1!");

        // when & then
        assertThatThrownBy(() -> userService.setPassword(loginUserId, request))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessage("Invalid_Password");

        verify(userRepository, never()).findByUserIdAndDeletedFalse(anyLong());
        verify(passwordEncoder, never()).encode(anyString());
    }

    @Test
    @DisplayName("비밀번호 수정 시 비밀번호가 일치하면 암호화된 비밀번호로 변경된다")
    void setPasswordSuccess() {
        // given
        Long loginUserId = 1L;

        User loginUser = new User(
                "test@test.com",
                "old-encoded-password",
                "tester",
                "profile.png",
                0
        );

        ReflectionTestUtils.setField(loginUser, "userId", loginUserId);

        UserPasswordRequestDto request = createUserPasswordRequest(
                "NewPassword1!",
                "NewPassword1!"
        );

        when(userRepository.findByUserIdAndDeletedFalse(loginUserId))
                .thenReturn(Optional.of(loginUser));

        when(passwordEncoder.encode("NewPassword1!"))
                .thenReturn("new-encoded-password");

        // when
        userService.setPassword(loginUserId, request);

        // then
        verify(userRepository).findByUserIdAndDeletedFalse(loginUserId);
        verify(passwordEncoder).encode("NewPassword1!");

        assertThat(loginUser.getPassword()).isEqualTo("new-encoded-password");
        assertThat(loginUser.getPassword()).isNotEqualTo("NewPassword1!");
    }

    @Test
    @DisplayName("회원 탈퇴 시 로그인 유저가 없으면 No_User 예외가 발생한다")
    void deleteUserFailByNoUser() {
        // given
        Long loginUserId = 1L;

        when(userRepository.findByUserIdAndDeletedFalse(loginUserId))
                .thenReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> userService.deleteUser(loginUserId))
                .isInstanceOf(DataNullException.class)
                .hasMessage("No_User");

        verify(userRepository).findByUserIdAndDeletedFalse(loginUserId);
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

    @Test
    @DisplayName("회원 탈퇴 시 유저가 삭제 처리되고 닉네임과 프로필 이미지가 마스킹된다")
    void deleteUserSuccess() {
        // given
        Long loginUserId = 1L;

        User loginUser = new User(
                "test@test.com",
                "encoded-password",
                "tester",
                "profile.png",
                0
        );

        ReflectionTestUtils.setField(loginUser, "userId", loginUserId);

        when(userRepository.findByUserIdAndDeletedFalse(loginUserId))
                .thenReturn(Optional.of(loginUser));

        // when
        userService.deleteUser(loginUserId);

        // then
        verify(userRepository).findByUserIdAndDeletedFalse(loginUserId);

        assertThat(loginUser.isDeleted()).isTrue();
        assertThat(loginUser.getNickname()).isEqualTo("삭제된 유저");
        assertThat(loginUser.getProfileImage()).isNull();
    }

    @Test
    @DisplayName("탈퇴 후 같은 이메일로 재가입하면 이전 누적 신고 수를 계승한다")
    void createUserWithPreviousReceivedReportCount() {
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
                .thenReturn(false);

        when(userRepository.findMaxReceivedReportCountByEmailIncludingDeleted("test@test.com"))
                .thenReturn(5);

        when(passwordEncoder.encode("Password1!"))
                .thenReturn("encoded-password");

        when(userRepository.save(any(User.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        // when
        UserResponseDto response = userService.createUser(request);

        // then
        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);

        verify(userRepository).save(userCaptor.capture());

        User savedUser = userCaptor.getValue();

        assertThat(savedUser.getEmail()).isEqualTo("test@test.com");
        assertThat(savedUser.getPassword()).isEqualTo("encoded-password");
        assertThat(savedUser.getNickname()).isEqualTo("tester");
        assertThat(savedUser.getProfileImage()).isEqualTo("profile.png");
        assertThat(savedUser.getReceivedReportCount()).isEqualTo(5);
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

    private UserPasswordRequestDto createUserPasswordRequest(
            String password,
            String passwordCheck
    ) {
        UserPasswordRequestDto request = new UserPasswordRequestDto();

        ReflectionTestUtils.setField(request, "password", password);
        ReflectionTestUtils.setField(request, "passwordCheck", passwordCheck);

        return request;
    }
}
