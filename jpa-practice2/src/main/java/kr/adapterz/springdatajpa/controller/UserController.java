package kr.adapterz.springdatajpa.controller;


import kr.adapterz.springdatajpa.dto.UserInfoDto;
import kr.adapterz.springdatajpa.entity.User;
import kr.adapterz.springdatajpa.service.UserService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {
    private final UserService userService;

    @PostMapping
    public UserResponse create(@RequestBody CreateUserRequest request){
        User saved = userService.create(request.email,request.password,request.nickname);
        return UserResponse.of(saved);
    }

    @GetMapping("/{id}")
    public UserResponse findById(@PathVariable Long id){
        return UserResponse.of(userService.findById(id));
    }

    @PatchMapping("/{id}")
    public UserResponse update(@PathVariable Long id, @RequestBody UpdateUserRequest request) {
        User updatedUser = userService.update(id, request.nickname);
        return UserResponse.of(updatedUser);
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable Long id) {
        userService.delete(id);
    }

    @GetMapping("/search/nickname/keyword")
    public List<UserResponse> findByNicknameKeyword(@RequestParam String keyword) {
        return userService.findByNicknameKeyword(keyword).stream().map(UserResponse::of).toList();
    }

    @GetMapping("/exists/email")
    public boolean existsByEmail(@RequestParam String email) {
        return userService.existsByEmail(email);
    }

    @GetMapping("/count/nickname")
    public long countByNickname(@RequestParam String nickname) {
        return userService.countByNickname(nickname);
    }

    @GetMapping("/emails/nickname")
    public List<String> findEmailsByNickname(@RequestParam String nickname) {
        return userService.findEmailsByNickname(nickname);
    }

    @GetMapping("/search/nickname")
    public List<UserInfoDto> findUserByNicknameWithDto(@RequestParam String keyword) {
        return userService.findUserByNicknameWithDto(keyword);
    }

    @Data
    public static class UpdateUserRequest{
        private String nickname;
    }

    @Data
    public static class CreateUserRequest {
        private String email;
        private String password;
        private String nickname;
    }

    @Data
    public static class UserResponse{
        private Long id;
        private String email;
        private String nickname;

        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;
        private String createdBy;
        private String updatedBy;

        public static UserResponse of(User user){
            return new UserResponse(user.getId(),user.getEmail(),user.getNickname(),user.getCreatedAt(),
                    user.getUpdatedAt(),
                    user.getCreatedBy(),
                    user.getUpdatedBy());
        }

        public UserResponse(Long id, String email, String nickname, LocalDateTime createdAt,
                            LocalDateTime updatedAt,
                            String createdBy,
                            String updatedBy){
            this.id=id;
            this.email=email;
            this.nickname=nickname;
            this.createdAt = createdAt;
            this.updatedAt = updatedAt;
            this.createdBy = createdBy;
            this.updatedBy = updatedBy;
        }
    }
}
