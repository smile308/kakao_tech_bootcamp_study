package kr.adapterz.springdatajpa.auth;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
public class RefreshCookieProvider {

    public static final String COOKIE_NAME = "refreshToken";

    private final Duration refreshExpiration;
    private final boolean secure;
    private final String cookiePath;

    public RefreshCookieProvider(
            @Value("${jwt.refresh-expiration-millis}") long refreshExpirationMillis,
            @Value("${jwt.refresh-cookie-secure}") boolean secure,
            @Value("${jwt.refresh-cookie-path}") String cookiePath
    ) {
        this.refreshExpiration = Duration.ofMillis(refreshExpirationMillis);
        this.secure = secure;
        this.cookiePath = cookiePath;
    }

    public ResponseCookie createRefreshTokenCookie(String refreshToken) {
        return ResponseCookie.from(COOKIE_NAME, refreshToken)
                .httpOnly(true)
                .secure(secure)
                .sameSite("Strict")
                .path(cookiePath)
                .maxAge(refreshExpiration)
                .build();
    }

    public ResponseCookie createExpiredRefreshTokenCookie() {
        return ResponseCookie.from(COOKIE_NAME, "")
                .httpOnly(true)
                .secure(secure)
                .sameSite("Strict")
                .path(cookiePath)
                .maxAge(Duration.ZERO)
                .build();
    }
}
