package kr.adapterz.springdatajpa.dto.user;

import kr.adapterz.springdatajpa.entity.User;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class UserResponseDto {
    private Long user_id;
    private String message;

    public UserResponseDto(User user){
        this.message = "signup_success";
        this.user_id=user.getUserId();
    }
}
