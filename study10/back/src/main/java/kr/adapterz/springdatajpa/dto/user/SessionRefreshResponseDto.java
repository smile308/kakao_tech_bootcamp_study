package kr.adapterz.springdatajpa.dto.user;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class SessionRefreshResponseDto {

    private String message;
    private String accessToken;

    @JsonIgnore
    private String refreshToken;

    public SessionRefreshResponseDto(
            String accessToken,
            String refreshToken
    ) {
        this.message = "refresh_success";
        this.accessToken = accessToken;
        this.refreshToken = refreshToken;
    }
}
