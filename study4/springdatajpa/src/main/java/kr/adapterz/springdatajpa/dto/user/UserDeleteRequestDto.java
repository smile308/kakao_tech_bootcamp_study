package kr.adapterz.springdatajpa.dto.user;

import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class UserDeleteRequestDto {
    private Long user_id;
    private String access_session;
}
