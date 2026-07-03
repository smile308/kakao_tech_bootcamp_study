package kr.adapterz.springdatajpa.controller;

import jakarta.validation.Valid;
import kr.adapterz.springdatajpa.dto.user.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import kr.adapterz.springdatajpa.service.UserService;

@RestController
@RequestMapping("/users")
@RequiredArgsConstructor
public class UserController {
    private final UserService userService;

    //회원가입
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public UserResponseDto createUser(@Valid @RequestBody UserRequestDto request){
        return userService.createUser(request);
    }

    //내 회원정보 조회
    @GetMapping("/me")
    public UserInfoResponseDto getMyInfo(@RequestHeader("Authorization") String authorizationHeader){
        return userService.getMyInfo(authorizationHeader);
    }

    //회원 탈퇴
    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public UserDeleteResponseDto deleteUser(@RequestHeader("Authorization") String authorizationHeader){
        return userService.deleteUser(authorizationHeader);
    }
    //회원정보 수정
    @PatchMapping
    public UserPatchResponseDto patchUser(
            @RequestHeader("Authorization") String authorizationHeader,
            @Valid @RequestBody UserPatchRequestDto request
    ){
        return userService.patchUser(authorizationHeader, request);
    }
    //비밀번호 수정
    @PatchMapping("/password")
    public UserPasswordResponseDto setPassword(
            @RequestHeader("Authorization") String authorizationHeader,
            @Valid @RequestBody UserPasswordRequestDto request
    ){
        return userService.setPassword(authorizationHeader, request);
    }
}
