package kr.adapterz.springdatajpa.controller;

import jakarta.validation.Valid;
import kr.adapterz.springdatajpa.dto.user.*;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import kr.adapterz.springdatajpa.service.UserService;

@RestController
@RequestMapping("/users")
@RequiredArgsConstructor
public class UserController {
    private final UserService userService;

    //회원가입
    @PostMapping
    public UserResponseDto createUser(@Valid @RequestBody UserRequestDto request){
        return userService.createUser(request);
    }

    //회원 탈퇴
    @DeleteMapping
    public UserDeleteResponseDto deleteUser(@RequestBody UserDeleteRequestDto request){
        return userService.deleteUser(request);
    }
    //회원정보 수정
    @PatchMapping
    public UserPatchResponseDto patchUser(@Valid @RequestBody UserPatchRequestDto request){
        return userService.patchUser(request);
    }
    //비밀번호 수정
    @PatchMapping("/password")
    public UserPasswordResponseDto setPassword(@Valid @RequestBody UserPasswordRequestDto request){
        return userService.setPassword(request);
    }
}

