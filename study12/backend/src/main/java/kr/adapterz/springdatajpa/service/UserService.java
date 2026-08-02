package kr.adapterz.springdatajpa.service;

import kr.adapterz.springdatajpa.dto.user.*;
import kr.adapterz.springdatajpa.entity.User;
import kr.adapterz.springdatajpa.exception.DataNullException;
import kr.adapterz.springdatajpa.exception.ForbiddenException;
import kr.adapterz.springdatajpa.exception.InvalidRequestException;
import kr.adapterz.springdatajpa.repository.AuthSessionRepository;
import kr.adapterz.springdatajpa.repository.UserRepository;
import kr.adapterz.springdatajpa.validation.ImageDataUrlValidator;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Transactional
public class UserService {
    private final UserRepository userRepository;
    private final AuthSessionRepository authSessionRepository;
    private final PasswordEncoder passwordEncoder;


    public UserResponseDto createUser(UserRequestDto request) {
        if (!request.getPassword().equals(request.getPasswordCheck())) {
            throw new InvalidRequestException("Invalid_Password");
        }

        if (userRepository.existsByEmailAndDeletedFalse(request.getEmail())) {
            throw new InvalidRequestException("Existed_Email");
        }

        if (userRepository.existsByNicknameAndDeletedFalse(request.getNickname())) {
            throw new InvalidRequestException("Existed_Nickname");
        }

        int previousReceivedReportCount =
                userRepository.findMaxReceivedReportCountByEmailIncludingDeleted(
                        request.getEmail()
                );

        if (previousReceivedReportCount >= User.SUSPENSION_REPORT_THRESHOLD) {
            throw new InvalidRequestException("Suspended_Account");
        }

        ImageDataUrlValidator.validateProfileImage(request.getProfileImage());

        User user = new User(
                request.getEmail(),
                passwordEncoder.encode(request.getPassword()),
                request.getNickname(),
                request.getProfileImage(),
                previousReceivedReportCount
        );

        User savedUser = userRepository.save(user);

        return new UserResponseDto(savedUser);
    }

    @Transactional(readOnly = true)
    public UserInfoResponseDto getMyInfo(Long loginUserId){
        User user = getLoginUser(loginUserId);
        return new UserInfoResponseDto(user);
    }

    public UserDeleteResponseDto deleteUser(Long loginUserId){
        UserDeleteResponseDto userDeleteResponseDto = new UserDeleteResponseDto();
        User user = getLoginUser(loginUserId);
        if (user.isSuspended()) {
            throw new ForbiddenException("Suspended_Account");
        }
        user.delete();
        authSessionRepository.revokeAllActiveByUser(user, LocalDateTime.now());
        return userDeleteResponseDto;
    }
    public UserPatchResponseDto patchUser(Long loginUserId, UserPatchRequestDto request){
        User user = getLoginUser(loginUserId);

        if (userRepository.existsByNicknameAndDeletedFalseAndUserIdNot(request.getNickname(), user.getUserId())) {
            throw new InvalidRequestException("Existed_Nickname");
        }

        ImageDataUrlValidator.validateProfileImage(request.getProfileImage());

        user.update(request.getNickname(), request.getProfileImage());
        UserPatchResponseDto userPatchResponseDto = new UserPatchResponseDto();
        return userPatchResponseDto;
    }
    public UserPasswordResponseDto setPassword(Long loginUserId, UserPasswordRequestDto request){
        if (!request.getPassword().equals(request.getPasswordCheck())) {
            throw new InvalidRequestException("Invalid_Password");
        }

        User user = getLoginUser(loginUserId);

        if (!passwordEncoder.matches(request.getCurrentPassword(), user.getPassword())) {
            throw new InvalidRequestException("Invalid_Current_Password");
        }

        UserPasswordResponseDto userPasswordResponseDto = new UserPasswordResponseDto();

        user.changePassword(passwordEncoder.encode(request.getPassword()));
        authSessionRepository.revokeAllActiveByUser(user, LocalDateTime.now());
        return userPasswordResponseDto;
    }

    private User getLoginUser(Long loginUserId) {
        return userRepository.findByUserIdAndDeletedFalse(loginUserId)
                .orElseThrow(() -> new DataNullException("No_User"));
    }
}
