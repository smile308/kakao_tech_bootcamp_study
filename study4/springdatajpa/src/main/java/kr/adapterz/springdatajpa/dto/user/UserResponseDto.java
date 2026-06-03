package kr.adapterz.springdatajpa.dto.user;

import kr.adapterz.springdatajpa.entity.User;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class UserResponseDto {
    private Long user_id;

    public UserResponseDto(User user){
        this.user_id=user.getUser_id();
    }
}
