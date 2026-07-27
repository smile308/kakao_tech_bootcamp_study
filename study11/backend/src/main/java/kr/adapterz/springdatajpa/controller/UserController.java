package kr.adapterz.springdatajpa.controller;

import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import kr.adapterz.springdatajpa.auth.CustomUserDetails;
import kr.adapterz.springdatajpa.auth.RefreshCookieProvider;
import kr.adapterz.springdatajpa.dto.user.*;
import kr.adapterz.springdatajpa.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/users")
@RequiredArgsConstructor
public class UserController {
    private final UserService userService;
    private final RefreshCookieProvider refreshCookieProvider;

    //회원가입
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public UserResponseDto createUser(@Valid @RequestBody UserRequestDto request){
        return userService.createUser(request);
    }

    //내 회원정보 조회
    @GetMapping("/me")
    public UserInfoResponseDto getMyInfo(@AuthenticationPrincipal CustomUserDetails userDetails){
        return userService.getMyInfo(userDetails.getUserId());
    }

    //회원 탈퇴
    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public UserDeleteResponseDto deleteUser(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            HttpServletResponse response
    ){
        UserDeleteResponseDto responseDto = userService.deleteUser(userDetails.getUserId());
        expireRefreshTokenCookie(response);
        return responseDto;
    }
    //회원정보 수정
    @PatchMapping
    public UserPatchResponseDto patchUser(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Valid @RequestBody UserPatchRequestDto request
    ){
        return userService.patchUser(userDetails.getUserId(), request);
    }
    //비밀번호 수정
    @PatchMapping("/password")
    public UserPasswordResponseDto setPassword(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Valid @RequestBody UserPasswordRequestDto request,
            HttpServletResponse response
    ){
        UserPasswordResponseDto responseDto = userService.setPassword(userDetails.getUserId(), request);
        expireRefreshTokenCookie(response);
        return responseDto;
    }

    private void expireRefreshTokenCookie(HttpServletResponse response) {
        response.addHeader(
                HttpHeaders.SET_COOKIE,
                refreshCookieProvider.createExpiredRefreshTokenCookie().toString()
        );
    }
}
