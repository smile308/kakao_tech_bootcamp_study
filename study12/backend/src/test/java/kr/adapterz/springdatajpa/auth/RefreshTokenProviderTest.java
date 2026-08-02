package kr.adapterz.springdatajpa.auth;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

class RefreshTokenProviderTest {

    @Test
    void 리프레시_토큰은_URL_safe_Base64_형식으로_생성된다() {
        RefreshTokenProvider refreshTokenProvider =
                new RefreshTokenProvider(10_800_000);

        String refreshToken = refreshTokenProvider.createRefreshToken();

        assertAll(
                () -> assertThat(refreshToken).hasSize(43),
                () -> assertThat(refreshToken).doesNotContain("="),
                () -> assertThat(refreshToken)
                        .matches("^[A-Za-z0-9_-]+$")
        );
    }

    @Test
    void 같은_리프레시_토큰은_항상_같은_SHA256_해시로_변환된다() {
        RefreshTokenProvider refreshTokenProvider =
                new RefreshTokenProvider(10_800_000);

        String firstHash = refreshTokenProvider.hashRefreshToken("refresh-token");
        String secondHash = refreshTokenProvider.hashRefreshToken("refresh-token");
        String otherHash = refreshTokenProvider.hashRefreshToken("other-refresh-token");

        assertAll(
                () -> assertThat(firstHash).hasSize(64),
                () -> assertThat(firstHash).matches("^[0-9a-f]+$"),
                () -> assertThat(firstHash).isEqualTo(secondHash),
                () -> assertThat(firstHash).isNotEqualTo(otherHash)
        );
    }

    @Test
    void 리프레시_토큰_만료_시간은_설정된_시간만큼_뒤로_생성된다() {
        RefreshTokenProvider refreshTokenProvider =
                new RefreshTokenProvider(10_800_000);
        LocalDateTime before = LocalDateTime.now().plusHours(3).minusSeconds(1);

        LocalDateTime expirationTime = refreshTokenProvider.createExpirationTime();

        LocalDateTime after = LocalDateTime.now().plusHours(3).plusSeconds(1);

        assertThat(expirationTime)
                .isAfterOrEqualTo(before)
                .isBeforeOrEqualTo(after);
    }
}
