package kr.adapterz.springdatajpa.service;

import kr.adapterz.springdatajpa.dto.user.*;
import kr.adapterz.springdatajpa.entity.User;
import kr.adapterz.springdatajpa.exception.DataNullException;
import kr.adapterz.springdatajpa.exception.InvalidRequestException;
import kr.adapterz.springdatajpa.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class UserService {
    private final UserRepository userRepository;

    //회원가입
    public UserResponseDto createUser(UserRequestDto request){
        //비밀번호 확인
        if (!request.getPassword().equals(request.getPassword_check())) {
            throw new InvalidRequestException();
        }
        //이메일 중복 체크
        if (userRepository.existsEmail(request.getEmail())) {
            throw new InvalidRequestException();
        }
        //닉네임 중복 체크
        if (userRepository.existsNickname(request.getNickname())) {
            throw new InvalidRequestException();
        }
        User user = new User(
                request.getEmail(),
                request.getPassword(),
                request.getNickname(),
                request.getProfile_image()
        );
        User  savedUser = userRepository.save(user);

        return new UserResponseDto(savedUser);
    }
    //회원 탈퇴
    public UserDeleteResponseDto deleteUser(UserDeleteRequestDto request){
        UserDeleteResponseDto userDeleteResponseDto = new UserDeleteResponseDto();
        userRepository.deleteById(request.getUser_id());
        return userDeleteResponseDto;
    }
    //회원 정보 수정
    public UserPatchResponseDto patchUser(UserPatchRequestDto request){
        //닉네임 중복 체크
        if (userRepository.existsNickname(request.getNickname())) {
            throw new InvalidRequestException();
        }
        User user=userRepository.findId(request.getUser_id()).orElseThrow(()->new DataNullException());
        user.update(request.getNickname(),request.getProfile_image());
        UserPatchResponseDto userPatchResponseDto = new UserPatchResponseDto();
        return userPatchResponseDto;
    }
    //비밀번호 수정
    public UserPasswordResponseDto setPassword(UserPasswordRequestDto request){
        //비밀번호 확인
        if (!request.getPassword().equals(request.getPassword_check())) {
            throw new InvalidRequestException();
        }
        UserPasswordResponseDto userPasswordResponseDto = new UserPasswordResponseDto();
        User user=userRepository.findId(request.getUser_id()).orElseThrow(()->new DataNullException());
        user.setPassword(request.getPassword());
        return userPasswordResponseDto;
    }
}
