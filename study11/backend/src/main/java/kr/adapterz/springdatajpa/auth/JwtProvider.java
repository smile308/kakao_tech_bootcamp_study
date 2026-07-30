package kr.adapterz.springdatajpa.auth;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import kr.adapterz.springdatajpa.exception.AuthException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

@Component
public class JwtProvider {
    private static final String AUTH_VERSION_CLAIM = "authVersion";

    private final SecretKey secretKey;
    private final long accessExpirationMillis;

    public JwtProvider(
            @Value("${jwt.secret}") String secret,
            @Value("${jwt.access-expiration-millis}") long accessExpirationMillis
    ) {
        this.secretKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.accessExpirationMillis = accessExpirationMillis;
    }

    public String createAccessToken(Long userId, long authVersion) {
        Date now = new Date();
        Date expiration = new Date(now.getTime() + accessExpirationMillis);

        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim(AUTH_VERSION_CLAIM, authVersion)
                .issuedAt(now)
                .expiration(expiration)
                .signWith(secretKey)
                .compact();
    }

    public AccessTokenClaims getAccessTokenClaims(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(secretKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();

            Object authVersionClaim = claims.get(AUTH_VERSION_CLAIM);
            if (!(authVersionClaim instanceof Number authVersion)) {
                throw new AuthException("Invalid_Token");
            }

            return new AccessTokenClaims(
                    Long.valueOf(claims.getSubject()),
                    authVersion.longValue()
            );
        } catch (JwtException | IllegalArgumentException e) {
            throw new AuthException("Invalid_Token");
        }
    }
}
