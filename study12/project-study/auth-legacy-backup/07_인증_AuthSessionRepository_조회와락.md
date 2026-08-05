# 4단계-4. AuthSessionRepository

## 파일 위치와 책임

파일: `/Users/miles/Documents/GitHub/KTB4_Miles_Week12_Back/src/main/java/kr/adapterz/springdatajpa/repository/AuthSessionRepository.java`

이 Repository는 `AuthSession` Entity를 DB에서 조회·일괄 revoke·만료 삭제하는 계층이다. 특히 Refresh Token 조회에는 비관적 쓰기 잠금을 걸어 동시에 들어온 재발급 요청이 같은 row를 동시에 rotate하지 못하게 한다.

## 전체 주석본

```java
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

public interface AuthSessionRepository extends JpaRepository<AuthSession, Long> { // AuthSession의 기본 CRUD와 아래 custom query를 제공한다.

    @Lock(LockModeType.PESSIMISTIC_WRITE) // 조회한 row에 DB 비관적 쓰기 잠금을 요청한다.
    Optional<AuthSession> findByRefreshTokenHash(String refreshTokenHash); // hash로 한 세션을 조회하고 없으면 Optional.empty를 반환한다.

    @Modifying(clearAutomatically = true, flushAutomatically = true) // SELECT가 아닌 UPDATE query임을 Spring Data에 알린다.
    @Query("""
            UPDATE AuthSession authSession
            SET authSession.revokedAt = :revokedAt
            WHERE authSession.user = :user
              AND authSession.revokedAt IS NULL
            """) // 활성 상태인 특정 사용자의 모든 세션에 revoke 시각을 일괄 기록한다.
    int revokeAllActiveByUser( // 실제로 변경된 row 수를 반환한다.
            @Param("user") User user, // JPQL :user 파라미터에 연결할 사용자다.
            @Param("revokedAt") LocalDateTime revokedAt // JPQL :revokedAt 파라미터에 넣을 시각이다.
    );

    @Modifying(clearAutomatically = true, flushAutomatically = true) // DELETE query 실행을 표시한다.
    @Query("""
            DELETE FROM AuthSession authSession
            WHERE authSession.refreshExpiresAt <= :now
            """) // 만료 시각이 현재 시각 이전·동일한 세션을 삭제한다.
    int deleteAllExpiredAtOrBefore(@Param("now") LocalDateTime now); // 삭제된 row 수를 반환한다.
}
```

## 설명 주석본

```java
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

/*
 * 1. Repository 기본 타입
 * AuthSession Entity를 Long 기본키로 관리한다.
 * JpaRepository를 상속하므로 save, findById, delete 같은 기본 메서드가 이미 제공된다.
 */
public interface AuthSessionRepository extends JpaRepository<AuthSession, Long> {

    /*
     * 2. Refresh Token 조회와 비관적 쓰기 잠금
     * hash가 같은 AuthSession 한 건을 조회한다.
     * PESSIMISTIC_WRITE는 조회한 DB row를 transaction이 끝날 때까지 잠가
     * 다른 transaction이 같은 세션을 동시에 변경하지 못하게 한다.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<AuthSession> findByRefreshTokenHash(String refreshTokenHash);

    /*
     * 3. 사용자 세션 일괄 revoke
     * Entity의 field 이름으로 JPQL UPDATE를 작성한다.
     * revokedAt이 null인 활성 세션만 골라 현재 revoke 시각을 기록한다.
     * 반환 int는 실제로 UPDATE된 row 수다.
     */
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

    /*
     * 4. 만료 세션 정리
     * 현재 시각보다 만료 시각이 이르거나 같은 AuthSession을 DELETE한다.
     * AuthSessionCleanupScheduler가 주기적으로 현재 시각을 전달해 호출한다.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            DELETE FROM AuthSession authSession
            WHERE authSession.refreshExpiresAt <= :now
            """)
    int deleteAllExpiredAtOrBefore(@Param("now") LocalDateTime now);
}
```

## 코드 바로 아래 상세 설명

### 1. Repository 기본 타입

`JpaRepository<AuthSession, Long>`의 첫 번째 타입은 관리할 Entity, 두 번째 타입은 Entity 기본키 타입이다. 따라서 이 Repository는 `AuthSession` row를 `Long` ID 기준으로 관리한다. 직접 구현하지 않은 기본 CRUD 메서드는 Spring Data JPA가 인터페이스를 분석해 구현체를 만든다.

### 2. `findByRefreshTokenHash`

호출 파일: `/Users/miles/Documents/GitHub/KTB4_Miles_Week12_Back/src/main/java/kr/adapterz/springdatajpa/service/SessionService.java`

호출 위치:

- `deleteSession`: hash로 세션을 찾은 뒤 `revoke` 호출
- `refreshSession`: hash로 세션을 찾고 없으면 `Invalid_Refresh_Token` 예외

메서드 이름 `findByRefreshTokenHash`는 Spring Data JPA가 `refreshTokenHash` field 조건으로 조회 query를 파생한다는 뜻이다. 반환 타입이 `Optional<AuthSession>`이므로 결과가 없을 때 null 대신 빈 Optional을 반환한다.

`@Lock(PESSIMISTIC_WRITE)`가 있는 이유는 Refresh Token rotate와 연결되기 때문이다.

```text
Transaction A: AuthSession 조회 → row 잠금 → rotate → commit
Transaction B: 같은 AuthSession 조회 시도 → A commit까지 대기
```

이 잠금은 메서드 선언만으로 끝나는 것이 아니라 호출 Service의 transaction 안에서 DB transaction과 함께 적용된다.

### 3. `revokeAllActiveByUser`

호출 파일: `UserService.java`

호출 위치:

- 사용자 탈퇴 처리 후
- 비밀번호 변경 처리 후

JPQL은 table·column 이름이 아니라 Entity와 Java field 이름을 사용한다.

```sql
UPDATE AuthSession authSession
SET authSession.revokedAt = :revokedAt
WHERE authSession.user = :user
  AND authSession.revokedAt IS NULL
```

- `AuthSession`: Entity 이름
- `authSession.revokedAt`: Entity field
- `authSession.user`: User 연관관계 field
- `:user`, `:revokedAt`: named parameter
- `IS NULL`: 아직 revoke되지 않은 세션만 선택

`@Param("user")`와 `@Param("revokedAt")`는 Java 매개변수와 JPQL의 `:user`, `:revokedAt`을 연결한다. `@Modifying`이 없으면 Spring Data가 이 query를 조회 query로 오해할 수 있다.

### 4. `deleteAllExpiredAtOrBefore`

호출 파일: `/Users/miles/Documents/GitHub/KTB4_Miles_Week12_Back/src/main/java/kr/adapterz/springdatajpa/service/AuthSessionCleanupScheduler.java`

호출 메서드: `deleteExpiredSessions()`

스케줄러가 `LocalDateTime.now()`를 `now`로 전달하면 다음 조건에 맞는 row를 삭제한다.

```text
refreshExpiresAt <= now
```

즉 만료 시각이 현재와 같거나 과거인 세션을 정리한다. 반환된 `int`는 삭제된 row 수지만 현재 Scheduler는 그 값을 별도로 사용하지 않는다.

## 새 문법·개념

- Spring Data derived query: 메서드 이름을 분석해 기본 SELECT query를 생성한다.
- `Optional<T>`: 조회 결과가 없을 수 있음을 타입으로 표현한다.
- `@Lock`: Repository query에 JPA lock mode를 지정한다.
- `@Modifying`: UPDATE·DELETE처럼 DB 상태를 바꾸는 query임을 표시한다.
- Java text block `"""`: 여러 줄 JPQL 문자열을 작성한다.
- named parameter `:name`: `@Param("name")`과 연결되는 JPQL 매개변수다.

## 다음 파일

`SessionService.java`의 `refreshSession`에서 이 Repository 조회, `isActive`, `rotate`, 새 token 응답이 하나의 transaction으로 어떻게 이어지는지 확인한다.

현재 파일 진행률: **29개 확인 완료 / 최소 학습 대상 213개 = 약 13.6%**
