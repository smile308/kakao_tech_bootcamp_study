package kr.adapterz.springdatajpa.service;

import kr.adapterz.springdatajpa.dto.user.UserPasswordRequestDto;
import kr.adapterz.springdatajpa.dto.user.UserPatchRequestDto;
import kr.adapterz.springdatajpa.dto.user.UserRequestDto;
import kr.adapterz.springdatajpa.dto.user.UserResponseDto;
import kr.adapterz.springdatajpa.entity.User;
import kr.adapterz.springdatajpa.exception.DataNullException;
import kr.adapterz.springdatajpa.exception.ForbiddenException;
import kr.adapterz.springdatajpa.exception.InvalidRequestException;
import kr.adapterz.springdatajpa.repository.AuthSessionRepository;
import kr.adapterz.springdatajpa.repository.UserRepository;
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

    private static final String VALID_PROFILE_IMAGE = "data:image/png;base64,iVBORw0KGgo=";

    @Mock
    private UserRepository userRepository;

    @Mock
    private AuthSessionRepository authSessionRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private UserService userService;

    @Test
    void 회원가입_시_비밀번호와_비밀번호_확인이_다르면_Invalid_Password_예외가_발생한다() {
        UserRequestDto request = createUserRequest(
                "test@test.com",
                "Password1!",
                "WrongPassword1!",
                "tester",
                "profile.png"
        );

        assertThatThrownBy(() -> userService.createUser(request))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessage("Invalid_Password");

        verify(userRepository, never()).existsByEmailAndDeletedFalse(anyString());
        verify(userRepository, never()).existsByNicknameAndDeletedFalse(anyString());
        verify(userRepository, never()).save(any(User.class));
        verify(passwordEncoder, never()).encode(anyString());
    }

    @Test
    void 회원가입_시_이미_존재하는_이메일이면_Existed_Email_예외가_발생한다() {
        UserRequestDto request = createUserRequest(
                "test@test.com",
                "Password1!",
                "Password1!",
                "tester",
                "profile.png"
        );

        when(userRepository.existsByEmailAndDeletedFalse("test@test.com"))
                .thenReturn(true);

        assertThatThrownBy(() -> userService.createUser(request))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessage("Existed_Email");

        verify(userRepository).existsByEmailAndDeletedFalse("test@test.com");
        verify(userRepository, never()).existsByNicknameAndDeletedFalse(anyString());
        verify(userRepository, never()).save(any(User.class));
        verify(passwordEncoder, never()).encode(anyString());
    }

    @Test
    void 회원가입_시_이미_존재하는_닉네임이면_Existed_Nickname_예외가_발생한다() {
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

        assertThatThrownBy(() -> userService.createUser(request))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessage("Existed_Nickname");

        verify(userRepository).existsByEmailAndDeletedFalse("test@test.com");
        verify(userRepository).existsByNicknameAndDeletedFalse("tester");
        verify(userRepository, never()).save(any(User.class));
        verify(passwordEncoder, never()).encode(anyString());
    }

    @Test
    void 회원가입_시_이메일과_닉네임이_중복되지_않으면_입력값과_암호화된_비밀번호로_유저가_저장된다() {
        UserRequestDto request = createUserRequest(
                "test@test.com",
                "Password1!",
                "Password1!",
                "tester",
                VALID_PROFILE_IMAGE
        );

        User savedUser = new User(
                "test@test.com",
                "encoded-password",
                "tester",
                VALID_PROFILE_IMAGE,
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

        userService.createUser(request);

        verify(userRepository).existsByEmailAndDeletedFalse("test@test.com");
        verify(userRepository).existsByNicknameAndDeletedFalse("tester");
        verify(passwordEncoder).encode("Password1!");

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());

        User capturedUser = userCaptor.getValue();

        assertThat(capturedUser.getEmail()).isEqualTo("test@test.com");
        assertThat(capturedUser.getNickname()).isEqualTo("tester");
        assertThat(capturedUser.getProfileImage()).isEqualTo(VALID_PROFILE_IMAGE);

        assertThat(capturedUser.getPassword()).isEqualTo("encoded-password");
        assertThat(capturedUser.getPassword()).isNotEqualTo("Password1!");
    }

    @Test
    void 탈퇴한_계정의_누적_신고_수가_10회_이상이면_재가입할_수_없다() {
        UserRequestDto request = createUserRequest(
                "suspended@test.com",
                "Password1!",
                "Password1!",
                "tester",
                "profile.png"
        );

        when(userRepository.existsByEmailAndDeletedFalse("suspended@test.com"))
                .thenReturn(false);

        when(userRepository.existsByNicknameAndDeletedFalse("tester"))
                .thenReturn(false);

        when(userRepository.findMaxReceivedReportCountByEmailIncludingDeleted("suspended@test.com"))
                .thenReturn(10);

        assertThatThrownBy(() -> userService.createUser(request))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessage("Suspended_Account");

        verify(userRepository).findMaxReceivedReportCountByEmailIncludingDeleted("suspended@test.com");
        verify(userRepository, never()).save(any(User.class));
        verify(passwordEncoder, never()).encode(anyString());
    }

    @Test
    void 회원정보_수정_시_다른_사용자가_이미_쓰는_닉네임이면_Existed_Nickname_예외가_발생한다() {
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

        assertThatThrownBy(() -> userService.patchUser(loginUserId, request))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessage("Existed_Nickname");

        verify(userRepository).findByUserIdAndDeletedFalse(loginUserId);
        verify(userRepository).existsByNicknameAndDeletedFalseAndUserIdNot("duplicatedNickname", loginUserId);
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void 회원정보_수정_시_내_userId를_제외하고_닉네임_중복이_없으면_수정된다() {
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
                VALID_PROFILE_IMAGE
        );

        when(userRepository.findByUserIdAndDeletedFalse(loginUserId))
                .thenReturn(Optional.of(loginUser));
        when(userRepository.existsByNicknameAndDeletedFalseAndUserIdNot("newNickname", loginUserId))
                .thenReturn(false);

        userService.patchUser(loginUserId, request);

        verify(userRepository).findByUserIdAndDeletedFalse(loginUserId);
        verify(userRepository).existsByNicknameAndDeletedFalseAndUserIdNot("newNickname", loginUserId);

        assertThat(loginUser.getNickname()).isEqualTo("newNickname");
        assertThat(loginUser.getProfileImage()).isEqualTo(VALID_PROFILE_IMAGE);
    }

    @Test
    void 내_정보_조회_시_로그인_유저가_없으면_No_User_예외가_발생한다() {
        Long loginUserId = 1L;

        when(userRepository.findByUserIdAndDeletedFalse(loginUserId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.getMyInfo(loginUserId))
                .isInstanceOf(DataNullException.class)
                .hasMessage("No_User");

        verify(userRepository).findByUserIdAndDeletedFalse(loginUserId);
    }

    @Test
    void 비밀번호_수정_시_비밀번호_확인이_다르면_Invalid_Password_예외가_발생한다() {
        Long loginUserId = 1L;
        UserPasswordRequestDto request = createUserPasswordRequest(
                "CurrentPassword1!",
                "Password1!",
                "WrongPassword1!"
        );

        assertThatThrownBy(() -> userService.setPassword(loginUserId, request))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessage("Invalid_Password");

        verify(userRepository, never()).findByUserIdAndDeletedFalse(anyLong());
        verify(passwordEncoder, never()).encode(anyString());
    }

    @Test
    void 비밀번호_수정_시_현재_비밀번호가_다르면_변경하지_않는다() {
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
                "WrongPassword1!",
                "NewPassword1!",
                "NewPassword1!"
        );

        when(userRepository.findByUserIdAndDeletedFalse(loginUserId))
                .thenReturn(Optional.of(loginUser));
        when(passwordEncoder.matches("WrongPassword1!", "old-encoded-password"))
                .thenReturn(false);

        assertThatThrownBy(() -> userService.setPassword(loginUserId, request))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessage("Invalid_Current_Password");

        verify(passwordEncoder).matches("WrongPassword1!", "old-encoded-password");
        verify(passwordEncoder, never()).encode(anyString());
        assertThat(loginUser.getPassword()).isEqualTo("old-encoded-password");
        assertThat(loginUser.getAuthVersion()).isZero();
    }

    @Test
    void 비밀번호_수정_시_비밀번호가_일치하면_암호화된_비밀번호로_변경된다() {
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
                "CurrentPassword1!",
                "NewPassword1!",
                "NewPassword1!"
        );

        when(userRepository.findByUserIdAndDeletedFalse(loginUserId))
                .thenReturn(Optional.of(loginUser));

        when(passwordEncoder.matches("CurrentPassword1!", "old-encoded-password"))
                .thenReturn(true);

        when(passwordEncoder.encode("NewPassword1!"))
                .thenReturn("new-encoded-password");

        userService.setPassword(loginUserId, request);

        verify(userRepository).findByUserIdAndDeletedFalse(loginUserId);
        verify(passwordEncoder).matches("CurrentPassword1!", "old-encoded-password");
        verify(passwordEncoder).encode("NewPassword1!");

        assertThat(loginUser.getPassword()).isEqualTo("new-encoded-password");
        assertThat(loginUser.getPassword()).isNotEqualTo("NewPassword1!");
        assertThat(loginUser.getAuthVersion()).isEqualTo(1L);
    }

    @Test
    void 회원_탈퇴_시_로그인_유저가_없으면_No_User_예외가_발생한다() {
        Long loginUserId = 1L;

        when(userRepository.findByUserIdAndDeletedFalse(loginUserId))
                .thenReturn(Optional.empty());

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
    void 회원_탈퇴_시_유저가_삭제_처리되고_닉네임과_프로필_이미지가_마스킹된다() {
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

        userService.deleteUser(loginUserId);

        verify(userRepository).findByUserIdAndDeletedFalse(loginUserId);

        assertThat(loginUser.isDeleted()).isTrue();
        assertThat(loginUser.getNickname()).isEqualTo("삭제된 유저");
        assertThat(loginUser.getProfileImage()).isNull();
    }

    @Test
    void 누적_신고_수가_10회_이상인_계정은_탈퇴할_수_없다() {
        Long loginUserId = 1L;

        User loginUser = new User(
                "suspended@test.com",
                "encoded-password",
                "tester",
                "profile.png",
                10
        );

        ReflectionTestUtils.setField(loginUser, "userId", loginUserId);

        when(userRepository.findByUserIdAndDeletedFalse(loginUserId))
                .thenReturn(Optional.of(loginUser));

        assertThatThrownBy(() -> userService.deleteUser(loginUserId))
                .isInstanceOf(ForbiddenException.class)
                .hasMessage("Suspended_Account");

        verify(userRepository).findByUserIdAndDeletedFalse(loginUserId);
        assertThat(loginUser.isDeleted()).isFalse();
    }

    @Test
    void 탈퇴_후_같은_이메일로_재가입하면_이전_누적_신고_수를_계승한다() {
        UserRequestDto request = createUserRequest(
                "test@test.com",
                "Password1!",
                "Password1!",
                "tester",
                VALID_PROFILE_IMAGE
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

        UserResponseDto response = userService.createUser(request);

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);

        verify(userRepository).save(userCaptor.capture());

        User savedUser = userCaptor.getValue();

        assertThat(savedUser.getEmail()).isEqualTo("test@test.com");
        assertThat(savedUser.getPassword()).isEqualTo("encoded-password");
        assertThat(savedUser.getNickname()).isEqualTo("tester");
        assertThat(savedUser.getProfileImage()).isEqualTo(VALID_PROFILE_IMAGE);
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
            String currentPassword,
            String password,
            String passwordCheck
    ) {
        UserPasswordRequestDto request = new UserPasswordRequestDto();

        ReflectionTestUtils.setField(request, "currentPassword", currentPassword);
        ReflectionTestUtils.setField(request, "password", password);
        ReflectionTestUtils.setField(request, "passwordCheck", passwordCheck);

        return request;
    }
}
