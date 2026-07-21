package kr.adapterz.springdatajpa.entity;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

class AuthSessionTest {

    @Test
    @DisplayName("리프레시 세션 생성 시 기본값이 정상 설정된다")
    void createAuthSession() {
        // given
        User user = createUser();
        LocalDateTime refreshExpiresAt = LocalDateTime.now().plusHours(3);

        // when
        AuthSession authSession = new AuthSession(
                user,
                "refresh-token-hash",
                refreshExpiresAt
        );

        // then
        assertAll(
                () -> assertThat(authSession.getUser()).isEqualTo(user),
                () -> assertThat(authSession.getRefreshTokenHash())
                        .isEqualTo("refresh-token-hash"),
                () -> assertThat(authSession.getRefreshExpiresAt())
                        .isEqualTo(refreshExpiresAt),
                () -> assertThat(authSession.getCreatedAt()).isNotNull(),
                () -> assertThat(authSession.getRevokedAt()).isNull()
        );
    }

    @Test
    @DisplayName("폐기되지 않고 만료되지 않은 리프레시 세션만 활성 상태다")
    void isActive() {
        // given
        LocalDateTime now = LocalDateTime.of(2026, 7, 21, 12, 0);
        AuthSession authSession = new AuthSession(
                createUser(),
                "refresh-token-hash",
                now.plusHours(1)
        );

        // when & then
        assertThat(authSession.isActive(now)).isTrue();
        assertThat(authSession.isActive(now.plusHours(2))).isFalse();

        authSession.revoke(now.plusMinutes(10));

        assertThat(authSession.isActive(now.plusMinutes(20))).isFalse();
    }

    @Test
    @DisplayName("리프레시 세션 회전 시 토큰 해시와 만료 시간이 교체된다")
    void rotate() {
        // given
        AuthSession authSession = new AuthSession(
                createUser(),
                "old-refresh-token-hash",
                LocalDateTime.of(2026, 7, 21, 12, 0)
        );

        // when
        authSession.rotate(
                "new-refresh-token-hash",
                LocalDateTime.of(2026, 7, 21, 15, 0)
        );

        // then
        assertAll(
                () -> assertThat(authSession.getRefreshTokenHash())
                        .isEqualTo("new-refresh-token-hash"),
                () -> assertThat(authSession.getRefreshExpiresAt())
                        .isEqualTo(LocalDateTime.of(2026, 7, 21, 15, 0))
        );
    }

    @Test
    @DisplayName("리프레시 세션 폐기는 최초 폐기 시간만 유지한다")
    void revokeKeepsFirstRevokedAt() {
        // given
        AuthSession authSession = new AuthSession(
                createUser(),
                "refresh-token-hash",
                LocalDateTime.now().plusHours(3)
        );
        LocalDateTime firstRevokedAt = LocalDateTime.of(2026, 7, 21, 12, 0);
        LocalDateTime secondRevokedAt = LocalDateTime.of(2026, 7, 21, 13, 0);

        // when
        authSession.revoke(firstRevokedAt);
        authSession.revoke(secondRevokedAt);

        // then
        assertThat(authSession.getRevokedAt()).isEqualTo(firstRevokedAt);
    }

    private User createUser() {
        return new User(
                "test@test.com",
                "Password1!",
                "tester",
                "profile.png",
                0
        );
    }
}
