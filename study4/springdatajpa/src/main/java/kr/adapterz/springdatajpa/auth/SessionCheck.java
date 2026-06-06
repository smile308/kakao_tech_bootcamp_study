package kr.adapterz.springdatajpa.auth;

import jakarta.validation.Valid;
import kr.adapterz.springdatajpa.exception.InvalidRequestException;
import kr.adapterz.springdatajpa.exception.SessionException;
import org.springframework.stereotype.Component;

@Component
public class SessionCheck {
    private static final String session = "000000";

    public void check(String access_session) {
        if (!session.equals(access_session)) {
            throw new SessionException();
        }
    }
}
