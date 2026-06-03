package kr.adapterz.springdatajpa.dto.user;

import kr.adapterz.springdatajpa.entity.Session;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class SessionResponseDto {
    private String access_session;
    private Long user_id;

    public SessionResponseDto(Session session)
    {
        this.access_session=session.getAccess_session();
        this.user_id=session.getUser_id();
    }
}
