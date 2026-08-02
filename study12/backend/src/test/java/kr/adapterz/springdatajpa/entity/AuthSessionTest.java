package kr.adapterz.springdatajpa.entity;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

class AuthSessionTest {

    @Test
    void 리프레시_세션_생성_시_기본값이_정상_설정된다() {
        User user = createUser();
        LocalDateTime refreshExpiresAt = LocalDateTime.now().plusHours(3);

        AuthSession authSession = new AuthSession(
                user,
                "refresh-token-hash",
                refreshExpiresAt
        );

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
    void 폐기되지_않고_만료되지_않은_리프레시_세션만_활성_상태다() {
        LocalDateTime now = LocalDateTime.of(2026, 7, 21, 12, 0);
        AuthSession authSession = new AuthSession(
                createUser(),
                "refresh-token-hash",
                now.plusHours(1)
        );

        assertThat(authSession.isActive(now)).isTrue();
        assertThat(authSession.isActive(now.plusHours(2))).isFalse();

        authSession.revoke(now.plusMinutes(10));

        assertThat(authSession.isActive(now.plusMinutes(20))).isFalse();
    }

    @Test
    void 리프레시_세션_회전_시_토큰_해시와_만료_시간이_교체된다() {
        AuthSession authSession = new AuthSession(
                createUser(),
                "old-refresh-token-hash",
                LocalDateTime.of(2026, 7, 21, 12, 0)
        );

        authSession.rotate(
                "new-refresh-token-hash",
                LocalDateTime.of(2026, 7, 21, 15, 0)
        );

        assertAll(
                () -> assertThat(authSession.getRefreshTokenHash())
                        .isEqualTo("new-refresh-token-hash"),
                () -> assertThat(authSession.getRefreshExpiresAt())
                        .isEqualTo(LocalDateTime.of(2026, 7, 21, 15, 0))
        );
    }

    @Test
    void 리프레시_세션_폐기는_최초_폐기_시간만_유지한다() {
        AuthSession authSession = new AuthSession(
                createUser(),
                "refresh-token-hash",
                LocalDateTime.now().plusHours(3)
        );
        LocalDateTime firstRevokedAt = LocalDateTime.of(2026, 7, 21, 12, 0);
        LocalDateTime secondRevokedAt = LocalDateTime.of(2026, 7, 21, 13, 0);

        authSession.revoke(firstRevokedAt);
        authSession.revoke(secondRevokedAt);

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
