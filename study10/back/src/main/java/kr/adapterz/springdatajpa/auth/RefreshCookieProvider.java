package kr.adapterz.springdatajpa.auth;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
public class RefreshCookieProvider {

    public static final String COOKIE_NAME = "refreshToken";
    private static final String COOKIE_PATH = "/sessions";

    private final Duration refreshExpiration;
    private final boolean secure;

    public RefreshCookieProvider(
            @Value("${jwt.refresh-expiration-millis}") long refreshExpirationMillis,
            @Value("${jwt.refresh-cookie-secure}") boolean secure
    ) {
        this.refreshExpiration = Duration.ofMillis(refreshExpirationMillis);
        this.secure = secure;
    }

    public ResponseCookie createRefreshTokenCookie(String refreshToken) {
        return ResponseCookie.from(COOKIE_NAME, refreshToken)
                .httpOnly(true)
                .secure(secure)
                .sameSite("Strict")
                .path(COOKIE_PATH)
                .maxAge(refreshExpiration)
                .build();
    }

    public ResponseCookie createExpiredRefreshTokenCookie() {
        return ResponseCookie.from(COOKIE_NAME, "")
                .httpOnly(true)
                .secure(secure)
                .sameSite("Strict")
                .path(COOKIE_PATH)
                .maxAge(Duration.ZERO)
                .build();
    }
}
