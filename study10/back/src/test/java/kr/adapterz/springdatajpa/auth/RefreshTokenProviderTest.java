package kr.adapterz.springdatajpa.auth;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

class RefreshTokenProviderTest {

    @Test
    @DisplayName("리프레시 토큰은 URL safe Base64 형식으로 생성된다")
    void createRefreshToken() {
        // given
        RefreshTokenProvider refreshTokenProvider =
                new RefreshTokenProvider(10_800_000);

        // when
        String refreshToken = refreshTokenProvider.createRefreshToken();

        // then
        assertAll(
                () -> assertThat(refreshToken).hasSize(43),
                () -> assertThat(refreshToken).doesNotContain("="),
                () -> assertThat(refreshToken)
                        .matches("^[A-Za-z0-9_-]+$")
        );
    }

    @Test
    @DisplayName("같은 리프레시 토큰은 항상 같은 SHA-256 해시로 변환된다")
    void hashRefreshToken() {
        // given
        RefreshTokenProvider refreshTokenProvider =
                new RefreshTokenProvider(10_800_000);

        // when
        String firstHash = refreshTokenProvider.hashRefreshToken("refresh-token");
        String secondHash = refreshTokenProvider.hashRefreshToken("refresh-token");
        String otherHash = refreshTokenProvider.hashRefreshToken("other-refresh-token");

        // then
        assertAll(
                () -> assertThat(firstHash).hasSize(64),
                () -> assertThat(firstHash).matches("^[0-9a-f]+$"),
                () -> assertThat(firstHash).isEqualTo(secondHash),
                () -> assertThat(firstHash).isNotEqualTo(otherHash)
        );
    }

    @Test
    @DisplayName("리프레시 토큰 만료 시간은 설정된 시간만큼 뒤로 생성된다")
    void createExpirationTime() {
        // given
        RefreshTokenProvider refreshTokenProvider =
                new RefreshTokenProvider(10_800_000);
        LocalDateTime before = LocalDateTime.now().plusHours(3).minusSeconds(1);

        // when
        LocalDateTime expirationTime = refreshTokenProvider.createExpirationTime();

        // then
        LocalDateTime after = LocalDateTime.now().plusHours(3).plusSeconds(1);

        assertThat(expirationTime)
                .isAfterOrEqualTo(before)
                .isBeforeOrEqualTo(after);
    }
}
