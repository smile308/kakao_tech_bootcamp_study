package kr.adapterz.springdatajpa.repository;

import kr.adapterz.springdatajpa.entity.AuthSession;
import kr.adapterz.springdatajpa.entity.User;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Optional;

public interface AuthSessionRepository extends JpaRepository<AuthSession, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<AuthSession> findByRefreshTokenHash(String refreshTokenHash);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE AuthSession authSession
            SET authSession.revokedAt = :revokedAt
            WHERE authSession.user = :user
              AND authSession.revokedAt IS NULL
            """)
    int revokeAllActiveByUser(
            @Param("user") User user,
            @Param("revokedAt") LocalDateTime revokedAt
    );

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            DELETE FROM AuthSession authSession
            WHERE authSession.refreshExpiresAt <= :now
            """)
    int deleteAllExpiredAtOrBefore(@Param("now") LocalDateTime now);
}
