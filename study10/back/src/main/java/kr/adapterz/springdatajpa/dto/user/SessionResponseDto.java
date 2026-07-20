package kr.adapterz.springdatajpa.dto.user;

import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class SessionResponseDto {
    private String message;
    private String accessToken;
    private Long userId;

    public SessionResponseDto(String accessToken, Long userId) {
        this.message = "login_success";
        this.accessToken = accessToken;
        this.userId = userId;
    }
}
