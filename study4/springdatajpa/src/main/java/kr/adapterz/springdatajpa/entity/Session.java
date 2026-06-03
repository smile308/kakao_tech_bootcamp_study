package kr.adapterz.springdatajpa.entity;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public class Session {

    private String access_session;
    private Long user_id;
    public Session(Long user_id){
        access_session="000000";
        this.user_id=user_id;
    }

}
