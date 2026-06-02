package kr.adapterz.springdatajpa.controller;

import kr.adapterz.springdatajpa.dto.UserRequestDto;
import kr.adapterz.springdatajpa.dto.UserResponseDto;
import kr.adapterz.springdatajpa.entity.User;
import kr.adapterz.springdatajpa.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/users")
@RequiredArgsConstructor
public class UserController {

    private final UserRepository userRepository;


    @PostMapping
    @Transactional
    public UserResponseDto createUser(@RequestBody UserRequestDto request) {
        User user = new User(
                request.getEmail(),
                request.getPassword(),
                request.getNickname()
        );
        User savedUser = userRepository.save(user);
        return new UserResponseDto(savedUser);
    }
}