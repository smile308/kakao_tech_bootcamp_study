package kr.adapterz.springdatajpa.dto.user;

import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class UserRequestDto {
    private String email;
    private String password;
    private String password_check;
    private String nickname;
    private String profile_image;


}
