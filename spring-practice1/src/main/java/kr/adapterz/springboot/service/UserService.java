package kr.adapterz.springboot.service;

import kr.adapterz.springboot.dto.UserRequestDto;
import kr.adapterz.springboot.dto.UserResponseDto;
import kr.adapterz.springboot.entity.User;
import kr.adapterz.springboot.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import kr.adapterz.springboot.exception.NotFoundException;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;

    @Transactional
    public UserResponseDto createUser(UserRequestDto request) {
        User user = new User(
                request.getEmail(),
                request.getPassword(),
                request.getNickname()
        );
        User savedUser = userRepository.save(user);
        return new UserResponseDto(savedUser);
    }

    @Transactional(readOnly = true)
    public UserResponseDto getUser(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("USER_NOT_FOUND"));
        return new UserResponseDto(user);
    }

    @Transactional
    public UserResponseDto updateNickname(Long userId, UserRequestDto request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() ->  new NotFoundException("USER_NOT_FOUND"));
        user.changeNickname(request.getNickname());
        return new UserResponseDto(user);
    }

    @Transactional
    public void deleteUser(Long userId) {
        userRepository.deleteById(userId);
    }
}
