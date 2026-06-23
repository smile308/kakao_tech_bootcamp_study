package kr.adapterz.springdatajpa.dto.user;

import kr.adapterz.springdatajpa.entity.Session;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class SessionResponseDto {
    private String message;
    private String accessSession;
    private Long userId;


    public SessionResponseDto(Session session)
    {
        this.message = "login_success";
        this.accessSession=session.getAccessSession();
        this.userId=session.getUserId();
    }
}
