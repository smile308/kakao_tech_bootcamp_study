package kr.adapterz.springboot.controller;

import jakarta.validation.Valid;
import kr.adapterz.springboot.dto.UserRequestDto;
import kr.adapterz.springboot.dto.UserResponseDto;
import kr.adapterz.springboot.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import kr.adapterz.springboot.exception.NotFoundException;
@RestController
@RequestMapping("/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @PostMapping
    public UserResponseDto createUser(@Valid @RequestBody UserRequestDto request) {
        return userService.createUser(request);
    }

    @GetMapping("/{userId}")
    public UserResponseDto getUser(@PathVariable Long userId) {
        return userService.getUser(userId);
    }

    @PutMapping("/{userId}")
    public UserResponseDto updateNickname(
            @PathVariable Long userId,
            @Valid @RequestBody UserRequestDto request
    ) {
        return userService.updateNickname(userId, request);
    }

    @DeleteMapping("/{userId}")
    public void deleteUser(@PathVariable Long userId) {
        userService.deleteUser(userId);
    }
}