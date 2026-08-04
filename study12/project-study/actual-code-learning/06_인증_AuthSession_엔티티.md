# 4단계-3. AuthSession 엔티티

## 이 파일의 위치

파일: `/Users/miles/Documents/GitHub/KTB4_Miles_Week12_Back/src/main/java/kr/adapterz/springdatajpa/entity/AuthSession.java`

이 Entity는 Refresh Token 자체를 저장하는 객체가 아니다. Refresh Token의 hash, 만료 시각, revoke 시각을 사용자와 연결해 DB에 저장하는 인증 세션 객체다.

## 전체 주석본

```java
package kr.adapterz.springdatajpa.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Getter // 각 field를 읽는 getter를 Lombok이 생성한다.
@Entity // 이 class를 JPA가 DB table과 연결되는 Entity로 관리한다.
@Table( // table 이름과 조회용 index를 지정한다.
        name = "auth_sessions", // 실제 DB table 이름이다.
        indexes = {
                @Index(name = "idx_auth_sessions_user_id", columnList = "user_id"), // 사용자별 세션 조회 index다.
                @Index(name = "idx_auth_sessions_refresh_expires_at", columnList = "refresh_expires_at") // 만료 세션 정리 index다.
        }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED) // JPA가 객체를 복원할 protected 기본 생성자를 만든다.
public class AuthSession {

    @Id // 기본키 field다.
    @GeneratedValue(strategy = GenerationType.IDENTITY) // DB auto-increment 방식으로 ID를 만든다.
    @Column(name = "auth_session_id") // Java field와 DB column 이름을 연결한다.
    private Long authSessionId;

    @ManyToOne(fetch = FetchType.LAZY) // 여러 AuthSession이 하나의 User를 참조하며, User는 필요할 때 조회한다.
    @JoinColumn(name = "user_id", nullable = false) // 외래키 column이며 null을 허용하지 않는다.
    private User user;

    @Column(name = "refresh_token_hash", nullable = false, unique = true, length = 64) // hash를 필수·유일한 64자 문자열로 저장한다.
    private String refreshTokenHash;

    @Column(name = "refresh_expires_at", nullable = false) // Refresh Token 만료 시각을 저장한다.
    private LocalDateTime refreshExpiresAt;

    @Column(name = "created_at", nullable = false, updatable = false) // 생성 시각이며 수정하지 않는다.
    private LocalDateTime createdAt;

    @Column(name = "revoked_at") // revoke된 시각이며 아직 유효하면 null이다.
    private LocalDateTime revokedAt;

    public AuthSession( // 로그인 성공 시 새 인증 세션을 만드는 도메인 생성자다.
            User user, // 인증된 사용자 Entity다.
            String refreshTokenHash, // 원문이 아닌 Refresh Token hash다.
            LocalDateTime refreshExpiresAt // 새 세션의 만료 시각이다.
    ) {
        this.user = user; // 사용자 연관관계를 저장한다.
        this.refreshTokenHash = refreshTokenHash; // hash를 저장한다.
        this.refreshExpiresAt = refreshExpiresAt; // 만료 시각을 저장한다.
        this.createdAt = LocalDateTime.now(); // 생성 시각을 현재 시각으로 기록한다.
        this.revokedAt = null; // 새 세션은 아직 revoke되지 않았다.
    }

    public boolean isActive(LocalDateTime now) { // 주어진 시각 기준으로 세션이 유효한지 판정한다.
        return revokedAt == null && refreshExpiresAt.isAfter(now); // revoke되지 않았고 만료 시각이 현재보다 뒤인지 확인한다.
    }

    public void rotate( // Refresh Token 재발급 때 같은 row의 token 정보를 교체한다.
            String refreshTokenHash, // 새 원문에서 계산한 hash다.
            LocalDateTime refreshExpiresAt // 새 만료 시각이다.
    ) {
        this.refreshTokenHash = refreshTokenHash; // 기존 hash를 새 hash로 바꾼다.
        this.refreshExpiresAt = refreshExpiresAt; // 기존 만료 시각을 새 시각으로 바꾼다.
    }

    public void revoke(LocalDateTime revokedAt) { // 로그아웃 등으로 세션을 무효화한다.
        if (this.revokedAt == null) { // 이미 revoke된 세션인지 확인한다.
            this.revokedAt = revokedAt; // 최초 revoke 시각만 기록한다.
        }
    }
}
```

## 목차 주석본

```java
package kr.adapterz.springdatajpa.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

//1
@Getter
@Entity
@Table(
        name = "auth_sessions",
        indexes = {
                @Index(name = "idx_auth_sessions_user_id", columnList = "user_id"),
                @Index(name = "idx_auth_sessions_refresh_expires_at", columnList = "refresh_expires_at")
        }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AuthSession {

    //2
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "auth_session_id")
    private Long authSessionId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "refresh_token_hash", nullable = false, unique = true, length = 64)
    private String refreshTokenHash;

    @Column(name = "refresh_expires_at", nullable = false)
    private LocalDateTime refreshExpiresAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "revoked_at")
    private LocalDateTime revokedAt;

    //3
    public AuthSession(
            User user,
            String refreshTokenHash,
            LocalDateTime refreshExpiresAt
    ) {
        this.user = user;
        this.refreshTokenHash = refreshTokenHash;
        this.refreshExpiresAt = refreshExpiresAt;
        this.createdAt = LocalDateTime.now();
        this.revokedAt = null;
    }

    //4
    public boolean isActive(LocalDateTime now) {
        return revokedAt == null && refreshExpiresAt.isAfter(now);
    }

    //5
    public void rotate(
            String refreshTokenHash,
            LocalDateTime refreshExpiresAt
    ) {
        this.refreshTokenHash = refreshTokenHash;
        this.refreshExpiresAt = refreshExpiresAt;
    }

    //6
    public void revoke(LocalDateTime revokedAt) {
        if (this.revokedAt == null) {
            this.revokedAt = revokedAt;
        }
    }
}
```

## 코드 바로 아래 설명

### 1. Entity·Table·생성자 annotation

- `@Entity`: JPA가 이 class를 영속화 대상으로 관리한다.
- `@Table(name = "auth_sessions")`: class 이름이 아니라 `auth_sessions` table에 매핑한다.
- 두 `@Index`는 사용자별 세션 조회와 만료 세션 정리 조회를 빠르게 하기 위한 DB index다.
- `@NoArgsConstructor(access = PROTECTED)`: 애플리케이션이 임의로 빈 Entity를 만들기보다 JPA가 조회 결과를 복원할 때 사용하도록 생성자 접근을 제한한다.

### 2. field와 DB column

- `authSessionId`: DB가 자동 생성하는 기본키다.
- `user`: `user_id` 외래키로 User와 연결된다. `LAZY`이므로 AuthSession을 읽는 순간 User 전체를 즉시 읽는다고 단정할 수 없다.
- `refreshTokenHash`: 원문이 아닌 hash이며 `unique = true`라 같은 hash를 두 row에 저장할 수 없다.
- `refreshExpiresAt`: Refresh Token의 만료 기준이다.
- `createdAt`: 생성 시각이며 `updatable = false`다.
- `revokedAt`: revoke 전에는 null이고 revoke 후 시각이 들어간다.

### 3. AuthSession 생성자

호출 파일: `SessionService.java`

호출 위치: 로그인 성공 후 `new AuthSession(user, refreshTokenHash, createExpirationTime())`.

생성자는 원문 Refresh Token이 아니라 hash를 받는다. 새 Entity는 현재 시각을 `createdAt`에 저장하고 `revokedAt`은 null로 시작한다. 이후 `authSessionRepository.save(authSession)`이 이 객체를 INSERT 대상으로 만든다.

### 4. isActive

호출 파일: `SessionService.java`의 `refreshSession`.

두 조건을 모두 만족해야 true다.

1. `revokedAt == null`: 로그아웃 등으로 무효화되지 않았다.
2. `refreshExpiresAt.isAfter(now)`: 현재 시각보다 만료 시각이 뒤에 있다.

둘 중 하나라도 false면 Refresh Token을 사용할 수 없는 세션으로 판단한다.

### 5. rotate

호출 파일: `SessionService.java`의 `refreshSession`.

기존 AuthSession row를 새로 만들지 않고, 같은 Entity의 hash와 만료 시각을 새 값으로 변경한다. JPA 영속 상태의 field가 바뀌면 transaction flush 시 UPDATE가 발생한다. 이 메서드 자체가 repository save를 호출하지 않는 이유는 호출자가 이미 조회한 영속 Entity를 transaction 안에서 변경하기 때문이다.

### 6. revoke

호출 파일: `SessionService.java`의 `deleteSession`.

첫 번째 revoke에서만 시각을 기록한다. 이미 `revokedAt`이 있는 세션에 다시 로그아웃 요청이 와도 기존 최초 시각을 덮어쓰지 않는다. 이후 `isActive`의 첫 번째 조건이 false가 되어 Refresh가 거부된다.

## 다음 파일

`repository/AuthSessionRepository.java`에서 이 Entity를 어떤 query와 lock으로 조회·수정하는지 읽는다.

현재 파일 진행률: **28개 확인 완료 / 최소 학습 대상 213개 = 약 13.1%**
