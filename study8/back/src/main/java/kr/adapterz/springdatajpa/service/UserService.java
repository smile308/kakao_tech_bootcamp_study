package kr.adapterz.springdatajpa.service;

import kr.adapterz.springdatajpa.dto.user.*;
import kr.adapterz.springdatajpa.entity.User;
import kr.adapterz.springdatajpa.exception.DataNullException;
import kr.adapterz.springdatajpa.exception.InvalidRequestException;
import kr.adapterz.springdatajpa.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class UserService {
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;


    // 회원가입
    public UserResponseDto createUser(UserRequestDto request) {
        // 비밀번호 확인
        if (!request.getPassword().equals(request.getPasswordCheck())) {
            throw new InvalidRequestException("Invalid_Password");
        }

        // 활성 이메일 중복 체크
        if (userRepository.existsByEmailAndDeletedFalse(request.getEmail())) {
            throw new InvalidRequestException("Existed_Email");
        }

        // 활성 닉네임 중복 체크
        if (userRepository.existsByNicknameAndDeletedFalse(request.getNickname())) {
            throw new InvalidRequestException("Existed_Nickname");
        }

        // 추가: 같은 이메일의 삭제 계정까지 포함해서 기존 누적 신고 수 조회
        int previousReceivedReportCount =
                userRepository.findMaxReceivedReportCountByEmailIncludingDeleted(
                        request.getEmail()
                );

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

    //내 회원정보 조회
    @Transactional(readOnly = true)
    public UserInfoResponseDto getMyInfo(Long loginUserId){
        User user = getLoginUser(loginUserId);
        return new UserInfoResponseDto(user);
    }

    //회원 탈퇴
    public UserDeleteResponseDto deleteUser(Long loginUserId){
        UserDeleteResponseDto userDeleteResponseDto = new UserDeleteResponseDto();
        User user = getLoginUser(loginUserId);
        user.delete();
        return userDeleteResponseDto;
    }
    //회원 정보 수정
    public UserPatchResponseDto patchUser(Long loginUserId, UserPatchRequestDto request){
        User user = getLoginUser(loginUserId);

        //닉네임 중복 체크
        if (userRepository.existsByNicknameAndDeletedFalseAndUserIdNot(request.getNickname(), user.getUserId())) {
            throw new InvalidRequestException("Existed_Nickname");
        }

        user.update(request.getNickname(), request.getProfileImage());
        UserPatchResponseDto userPatchResponseDto = new UserPatchResponseDto();
        return userPatchResponseDto;
    }
    //비밀번호 수정
    public UserPasswordResponseDto setPassword(Long loginUserId, UserPasswordRequestDto request){
        //비밀번호 확인
        if (!request.getPassword().equals(request.getPasswordCheck())) {
            throw new InvalidRequestException("Invalid_Password");
        }
        UserPasswordResponseDto userPasswordResponseDto = new UserPasswordResponseDto();
        User user = getLoginUser(loginUserId);

        user.setPassword(passwordEncoder.encode(request.getPassword()));
        return userPasswordResponseDto;
    }

    private User getLoginUser(Long loginUserId) {
        return userRepository.findByUserIdAndDeletedFalse(loginUserId)
                .orElseThrow(() -> new DataNullException("No_User"));
    }
}
