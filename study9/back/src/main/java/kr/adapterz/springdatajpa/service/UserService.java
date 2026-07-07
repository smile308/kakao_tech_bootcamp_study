package kr.adapterz.springdatajpa.service;

import kr.adapterz.springdatajpa.auth.JwtProvider;
import kr.adapterz.springdatajpa.dto.user.*;
import kr.adapterz.springdatajpa.entity.User;
import kr.adapterz.springdatajpa.exception.DataNullException;
import kr.adapterz.springdatajpa.exception.InvalidRequestException;
import kr.adapterz.springdatajpa.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class UserService {
    private final UserRepository userRepository;
    private final JwtProvider jwtProvider;


    //회원가입
    public UserResponseDto createUser(UserRequestDto request){
        //비밀번호 확인
        if (!request.getPassword().equals(request.getPasswordCheck())) {
            throw new InvalidRequestException("Invalid_Password");
        }
        //이메일 중복 체크
        if (userRepository.existsByEmailAndDeletedFalse(request.getEmail())) {
            throw new InvalidRequestException("Existed_Email");
        }
        //닉네임 중복 체크
        if (userRepository.existsByNicknameAndDeletedFalse(request.getNickname())) {
            throw new InvalidRequestException("Existed_Nickname");
        }
        User user = new User(
                request.getEmail(),
                request.getPassword(),
                request.getNickname(),
                request.getProfileImage()
        );
        User  savedUser = userRepository.save(user);

        return new UserResponseDto(savedUser);
    }

    //내 회원정보 조회
    @Transactional(readOnly = true)
    public UserInfoResponseDto getMyInfo(String authorizationHeader){
        User user = getLoginUser(authorizationHeader);
        return new UserInfoResponseDto(user);
    }

    //회원 탈퇴
    public UserDeleteResponseDto deleteUser(String authorizationHeader){
        UserDeleteResponseDto userDeleteResponseDto = new UserDeleteResponseDto();
        User user = getLoginUser(authorizationHeader);
        user.delete();
        return userDeleteResponseDto;
    }
    //회원 정보 수정
    public UserPatchResponseDto patchUser(String authorizationHeader, UserPatchRequestDto request){
        User user = getLoginUser(authorizationHeader);

        //닉네임 중복 체크
        if (userRepository.existsByNicknameAndDeletedFalseAndUserIdNot(request.getNickname(), user.getUserId())) {
            throw new InvalidRequestException("Existed_Nickname");
        }

        user.update(request.getNickname(), request.getProfileImage());
        UserPatchResponseDto userPatchResponseDto = new UserPatchResponseDto();
        return userPatchResponseDto;
    }
    //비밀번호 수정
    public UserPasswordResponseDto setPassword(String authorizationHeader, UserPasswordRequestDto request){
        //비밀번호 확인
        if (!request.getPassword().equals(request.getPasswordCheck())) {
            throw new InvalidRequestException("Invalid_Password");
        }
        UserPasswordResponseDto userPasswordResponseDto = new UserPasswordResponseDto();
        User user = getLoginUser(authorizationHeader);

        user.setPassword(request.getPassword());
        return userPasswordResponseDto;
    }

    private User getLoginUser(String authorizationHeader) {
        Long loginUserId = jwtProvider.getUserIdFromAuthorizationHeader(authorizationHeader);
        return userRepository.findByUserIdAndDeletedFalse(loginUserId)
                .orElseThrow(() -> new DataNullException("No_User"));
    }
}
