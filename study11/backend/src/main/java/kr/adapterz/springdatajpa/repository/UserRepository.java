package kr.adapterz.springdatajpa.repository;

import kr.adapterz.springdatajpa.entity.User;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByUserIdAndDeletedFalse(Long userId);

    boolean existsByEmailAndDeletedFalse(String email);

    boolean existsByNicknameAndDeletedFalse(String nickname);

    boolean existsByNicknameAndDeletedFalseAndUserIdNot(String nickname, Long userId);

    Optional<User> findByEmailAndDeletedFalse(String email);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT user
            FROM User user
            WHERE user.userId = :userId
            """)
    Optional<User> findByUserIdForUpdate(@Param("userId") Long userId);

    @Query(
            value = """
                    SELECT COALESCE(MAX(received_report_count), 0)
                    FROM users
                    WHERE email = :email
                    """,
            nativeQuery = true
    )
    int findMaxReceivedReportCountByEmailIncludingDeleted(@Param("email") String email);
}
