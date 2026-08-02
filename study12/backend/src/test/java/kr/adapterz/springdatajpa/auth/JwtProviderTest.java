package kr.adapterz.springdatajpa.auth;

import kr.adapterz.springdatajpa.exception.AuthException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtProviderTest {

    private static final String SECRET =
            "test-jwt-secret-key-must-be-at-least-32-characters-long";
    private static final long ONE_HOUR_MILLIS = 3_600_000L;

    @Test
    void 발급한_액세스_토큰에서_사용자_ID와_인증_버전을_다시_꺼낼_수_있다() {
        JwtProvider jwtProvider = new JwtProvider(SECRET, ONE_HOUR_MILLIS);

        String accessToken = jwtProvider.createAccessToken(42L, 3L);
        AccessTokenClaims tokenClaims =
                jwtProvider.getAccessTokenClaims(accessToken);

        assertThat(accessToken).isNotBlank();
        assertThat(tokenClaims.userId()).isEqualTo(42L);
        assertThat(tokenClaims.authVersion()).isEqualTo(3L);
    }

    @Test
    void 다른_비밀키로_서명한_토큰은_Invalid_Token_예외가_발생한다() {
        JwtProvider tokenIssuer = new JwtProvider(
                "forged-jwt-secret-key-must-be-at-least-32-characters",
                ONE_HOUR_MILLIS
        );
        JwtProvider tokenVerifier = new JwtProvider(SECRET, ONE_HOUR_MILLIS);
        String forgedToken = tokenIssuer.createAccessToken(42L, 0L);

        assertThatThrownBy(() -> tokenVerifier.getAccessTokenClaims(forgedToken))
                .isInstanceOf(AuthException.class)
                .hasMessage("Invalid_Token");
    }

    @Test
    void 만료된_토큰은_Invalid_Token_예외가_발생한다() {
        JwtProvider jwtProvider = new JwtProvider(SECRET, -1_000L);
        String expiredToken = jwtProvider.createAccessToken(42L, 0L);

        assertThatThrownBy(() -> jwtProvider.getAccessTokenClaims(expiredToken))
                .isInstanceOf(AuthException.class)
                .hasMessage("Invalid_Token");
    }
}
