package kr.adapterz.springdatajpa.auth;

import kr.adapterz.springdatajpa.exception.AuthException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtProviderTest {

    private static final String SECRET =
            "test-jwt-secret-key-must-be-at-least-32-characters-long";
    private static final long ONE_HOUR_MILLIS = 3_600_000L;

    @Test
    @DisplayName("발급한 액세스 토큰에서 사용자 ID를 다시 꺼낼 수 있다")
    void createAndParseAccessToken() {
        JwtProvider jwtProvider = new JwtProvider(SECRET, ONE_HOUR_MILLIS);

        String accessToken = jwtProvider.createAccessToken(42L);
        Long userId = jwtProvider.getUserId(accessToken);

        assertThat(accessToken).isNotBlank();
        assertThat(userId).isEqualTo(42L);
    }

    @Test
    @DisplayName("다른 비밀키로 서명한 토큰은 Invalid_Token 예외가 발생한다")
    void forgedTokenIsRejected() {
        JwtProvider tokenIssuer = new JwtProvider(
                "forged-jwt-secret-key-must-be-at-least-32-characters",
                ONE_HOUR_MILLIS
        );
        JwtProvider tokenVerifier = new JwtProvider(SECRET, ONE_HOUR_MILLIS);
        String forgedToken = tokenIssuer.createAccessToken(42L);

        assertThatThrownBy(() -> tokenVerifier.getUserId(forgedToken))
                .isInstanceOf(AuthException.class)
                .hasMessage("Invalid_Token");
    }

    @Test
    @DisplayName("만료된 토큰은 Invalid_Token 예외가 발생한다")
    void expiredTokenIsRejected() {
        JwtProvider jwtProvider = new JwtProvider(SECRET, -1_000L);
        String expiredToken = jwtProvider.createAccessToken(42L);

        assertThatThrownBy(() -> jwtProvider.getUserId(expiredToken))
                .isInstanceOf(AuthException.class)
                .hasMessage("Invalid_Token");
    }
}
