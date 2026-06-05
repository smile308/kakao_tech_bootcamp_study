package kr.adapterz.springdatajpa.controller;

import jakarta.validation.Valid;
import kr.adapterz.springdatajpa.dto.user.UserRequestDto;
import kr.adapterz.springdatajpa.dto.user.UserResponseDto;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
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
}

