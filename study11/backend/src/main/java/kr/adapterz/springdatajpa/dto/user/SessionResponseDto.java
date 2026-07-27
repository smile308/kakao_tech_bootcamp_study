package kr.adapterz.springdatajpa.dto.user;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class SessionResponseDto {
    private String message;
    private String accessToken;

    @JsonIgnore
    private String refreshToken;

    private Long userId;

    public SessionResponseDto(
            String accessToken,
            String refreshToken,
            Long userId
    ) {
        this.message = "login_success";
        this.accessToken = accessToken;
        this.refreshToken = refreshToken;
        this.userId = userId;
    }
}
