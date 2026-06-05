package kr.adapterz.springdatajpa.auth;

import kr.adapterz.springdatajpa.exception.InvalidRequestException;
import org.springframework.stereotype.Component;

@Component
public class SessionCheck {
    private static final String Valid = "000000";

    public void check(String access_session) {
        if (!Valid.equals(access_session)) {
            throw new InvalidRequestException();
        }
    }
}
