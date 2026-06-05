package kr.adapterz.springdatajpa.service;

import kr.adapterz.springdatajpa.dto.user.UserRequestDto;
import kr.adapterz.springdatajpa.dto.user.UserResponseDto;
import kr.adapterz.springdatajpa.entity.User;
import kr.adapterz.springdatajpa.exception.InvalidRequestException;
import kr.adapterz.springdatajpa.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class UserService {
    private final UserRepository userRepository;

    public UserResponseDto createUser(UserRequestDto request){
        if (!request.getPassword().equals(request.getPassword_check())) {
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



}
