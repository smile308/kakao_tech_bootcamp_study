package kr.adapterz.springdatajpa.auth;

import jakarta.validation.Valid;
import kr.adapterz.springdatajpa.exception.InvalidRequestException;
import kr.adapterz.springdatajpa.exception.SessionException;
import org.springframework.stereotype.Component;

@Component
public class SessionCheck {
    private static final String session = "000000";
    //입력 세션이 올바른지 확인하는 용도 현재는 "000000"으로 고정되어 있음.
    public void check(String access_session) {
        if (!session.equals(access_session)) {
            throw new SessionException();
        }
    }
}
