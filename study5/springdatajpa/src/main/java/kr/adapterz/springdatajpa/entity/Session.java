package kr.adapterz.springdatajpa.entity;

import jakarta.persistence.Column;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public class Session {

    @Column(name ="access_session", nullable = false)
    private String accessSession;
    @Column(name ="user_id", nullable = false)
    private Long userId;
    public Session(Long userId){
        accessSession="000000";
        this.userId=userId;
    }

}
