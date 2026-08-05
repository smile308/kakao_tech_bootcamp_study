# 인증 흐름 3. Refresh Token 재발급과 세션 rotation

Refresh Cookie를 받아 hash로 조회하고, 잠긴 AuthSession을 새 Refresh Token으로 교체한 뒤 새 Access Token과 Cookie를 반환하는 흐름이다.

실제 실행 흐름:

```text
api.js 401 → refreshPromise → POST /sessions/refresh
→ Cookie 입력 → hash → AuthSession row lock
→ active/user 상태 검사 → rotate
→ 새 Access Token + 새 Cookie → 원래 요청 재시도
```


---

## RefreshTokenProvider·RefreshCookieProvider·SessionRefreshResponseDto

- 실제 파일: `/Users/miles/Documents/GitHub/KTB4_Miles_Week12_Back/src/main/java/kr/adapterz/springdatajpa/auth/RefreshTokenProvider.java 및 RefreshCookieProvider.java`

## 1. RefreshTokenProvider

파일: `/Users/miles/Documents/GitHub/KTB4_Miles_Week12_Back/src/main/java/kr/adapterz/springdatajpa/auth/RefreshTokenProvider.java`

### 전체 주석본

```java
package kr.adapterz.springdatajpa.auth;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HexFormat;

@Component // Spring이 이 클래스를 Bean으로 등록한다.
public class RefreshTokenProvider {

    private static final int TOKEN_BYTE_LENGTH = 32; // 원문 token을 만들 난수 byte 수다.
    private final SecureRandom secureRandom = new SecureRandom(); // 예측하기 어려운 난수를 만든다.
    private final Duration refreshExpiration; // Refresh Token 유효 기간이다.

    public RefreshTokenProvider( // Bean 생성 시 설정값을 받는다.
            @Value("${jwt.refresh-expiration-millis}") long refreshExpirationMillis // YAML의 만료 시간이다.
    ) {
        this.refreshExpiration = Duration.ofMillis(refreshExpirationMillis); // 밀리초를 기간 객체로 바꾼다.
    }

    public String createRefreshToken() { // SessionService가 원문 token을 요청한다.
        byte[] tokenBytes = new byte[TOKEN_BYTE_LENGTH]; // 32 byte 배열을 만든다.
        secureRandom.nextBytes(tokenBytes); // 안전한 난수로 배열을 채운다.

        return Base64.getUrlEncoder() // URL·Cookie에 사용할 encoder를 선택한다.
                .withoutPadding() // 끝의 padding을 제거한다.
                .encodeToString(tokenBytes); // byte 배열을 문자열로 바꿔 반환한다.
    }

    public String hashRefreshToken(String refreshToken) { // 원문을 DB 저장용 hash로 바꾼다.
        try { // SHA-256 생성 실패를 처리한다.
            MessageDigest messageDigest = MessageDigest.getInstance("SHA-256"); // SHA-256 계산 객체를 만든다.
            byte[] tokenHash = messageDigest.digest( // 입력 byte의 hash를 계산한다.
                    refreshToken.getBytes(StandardCharsets.UTF_8) // 원문을 UTF-8 byte로 바꾼다.
            );

            return HexFormat.of().formatHex(tokenHash); // hash byte를 hexadecimal 문자열로 반환한다.
        } catch (NoSuchAlgorithmException e) { // SHA-256을 사용할 수 없을 때 발생한다.
            throw new IllegalStateException("SHA-256 is not available", e); // 애플리케이션 구성 오류로 바꾼다.
        }
    }

    public LocalDateTime createExpirationTime() { // AuthSession에 저장할 만료 시각을 만든다.
        return LocalDateTime.now().plus(refreshExpiration); // 현재 시각에 기간을 더한다.
    }
}
```

### 목차 주석본

```java
package kr.adapterz.springdatajpa.auth;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HexFormat;

//1
@Component
public class RefreshTokenProvider {
    private static final int TOKEN_BYTE_LENGTH = 32;
    private final SecureRandom secureRandom = new SecureRandom();
    private final Duration refreshExpiration;

    //1
    public RefreshTokenProvider(
            @Value("${jwt.refresh-expiration-millis}") long refreshExpirationMillis
    ) {
        this.refreshExpiration = Duration.ofMillis(refreshExpirationMillis);
    }

    //2
    public String createRefreshToken() {
        byte[] tokenBytes = new byte[TOKEN_BYTE_LENGTH];
        secureRandom.nextBytes(tokenBytes);
        return Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(tokenBytes);
    }

    //3
    public String hashRefreshToken(String refreshToken) {
        try {
            MessageDigest messageDigest = MessageDigest.getInstance("SHA-256");
            byte[] tokenHash = messageDigest.digest(
                    refreshToken.getBytes(StandardCharsets.UTF_8)
            );
            return HexFormat.of().formatHex(tokenHash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }

    //4
    public LocalDateTime createExpirationTime() {
        return LocalDateTime.now().plus(refreshExpiration);
    }
}
```

### 1. 선언부와 생성자

- `createRefreshToken` 호출 파일: `SessionService.java`의 `createSession`, `refreshSession`. 반환한 원문은 Cookie에 사용한다.
- `hashRefreshToken` 호출 파일: `SessionService.java`의 `createSession`, `refreshSession`, `deleteSession`. DB에는 원문이 아니라 hash를 저장한다.
- `createExpirationTime` 호출 위치: `new AuthSession(...)`과 `authSession.rotate(...)`. DB 세션 만료 시각을 계산한다.
- `MessageDigest.getInstance("SHA-256")`은 원문을 되돌릴 목적이 아닌 일방향 hash 계산 객체를 만든다.

### 2. createRefreshToken의 실행 흐름

`SessionService.createSession`과 `SessionService.refreshSession`이 호출한다. 32 byte 난수를 만들고 URL-safe Base64 문자열로 바꾼 뒤, 반환된 원문을 Cookie에 전달한다.

### 3. hashRefreshToken의 실행 흐름

`SessionService.createSession`, `refreshSession`, `deleteSession`이 호출한다. 원문을 UTF-8 byte로 바꾸고 SHA-256 hash를 계산한 뒤 hexadecimal 문자열로 반환한다. DB에는 이 반환값만 저장한다.

### 4. createExpirationTime의 실행 흐름

`new AuthSession(...)`과 `authSession.rotate(...)`에서 호출한다. 현재 시각에 Refresh Token 기간을 더해 DB 세션 만료 시각을 반환한다.

## 2. RefreshCookieProvider

파일: `/Users/miles/Documents/GitHub/KTB4_Miles_Week12_Back/src/main/java/kr/adapterz/springdatajpa/auth/RefreshCookieProvider.java`

### 전체 주석본

```java
package kr.adapterz.springdatajpa.auth;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component // SessionController가 주입받을 Bean이다.
public class RefreshCookieProvider {
    public static final String COOKIE_NAME = "refreshToken"; // 요청·응답 Cookie 이름이다.

    private final Duration refreshExpiration; // Cookie max-age 기간이다.
    private final boolean secure; // HTTPS 전용 여부다.
    private final String cookiePath; // Cookie 전송 경로다.

    public RefreshCookieProvider( // YAML 설정을 주입받는다.
            @Value("${jwt.refresh-expiration-millis}") long refreshExpirationMillis,
            @Value("${jwt.refresh-cookie-secure}") boolean secure,
            @Value("${jwt.refresh-cookie-path}") String cookiePath
    ) {
        this.refreshExpiration = Duration.ofMillis(refreshExpirationMillis); // 밀리초를 기간으로 바꾼다.
        this.secure = secure; // secure 설정을 저장한다.
        this.cookiePath = cookiePath; // path 설정을 저장한다.
    }

    public ResponseCookie createRefreshTokenCookie(String refreshToken) { // 유효한 Refresh Cookie를 만든다.
        return ResponseCookie.from(COOKIE_NAME, refreshToken) // 이름과 원문 값을 넣는다.
                .httpOnly(true) // JavaScript가 읽지 못하게 한다.
                .secure(secure) // 설정값에 따라 HTTPS 전용으로 만든다.
                .sameSite("Strict") // 다른 site 요청에 자동 전송하지 않는다.
                .path(cookiePath) // 지정 경로에서만 전송한다.
                .maxAge(refreshExpiration) // 유효 기간을 설정한다.
                .build(); // Cookie 객체를 완성한다.
    }

    public ResponseCookie createExpiredRefreshTokenCookie() { // 로그아웃 시 삭제 지시용 Cookie를 만든다.
        return ResponseCookie.from(COOKIE_NAME, "") // 같은 이름에 빈 값을 넣는다.
                .httpOnly(true) // 기존 Cookie와 같은 속성을 유지한다.
                .secure(secure)
                .sameSite("Strict")
                .path(cookiePath) // 같은 path여야 기존 Cookie와 일치한다.
                .maxAge(Duration.ZERO) // 브라우저에 즉시 만료를 지시한다.
                .build();
    }
}
```

### 목차 주석본

```java
package kr.adapterz.springdatajpa.auth;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import java.time.Duration;

//5
@Component
public class RefreshCookieProvider {
    public static final String COOKIE_NAME = "refreshToken";
    private final Duration refreshExpiration;
    private final boolean secure;
    private final String cookiePath;

    //5
    public RefreshCookieProvider(
            @Value("${jwt.refresh-expiration-millis}") long refreshExpirationMillis,
            @Value("${jwt.refresh-cookie-secure}") boolean secure,
            @Value("${jwt.refresh-cookie-path}") String cookiePath
    ) {
        this.refreshExpiration = Duration.ofMillis(refreshExpirationMillis);
        this.secure = secure;
        this.cookiePath = cookiePath;
    }

    //6
    public ResponseCookie createRefreshTokenCookie(String refreshToken) {
        return ResponseCookie.from(COOKIE_NAME, refreshToken)
                .httpOnly(true)
                .secure(secure)
                .sameSite("Strict")
                .path(cookiePath)
                .maxAge(refreshExpiration)
                .build();
    }

    //7
    public ResponseCookie createExpiredRefreshTokenCookie() {
        return ResponseCookie.from(COOKIE_NAME, "")
                .httpOnly(true)
                .secure(secure)
                .sameSite("Strict")
                .path(cookiePath)
                .maxAge(Duration.ZERO)
                .build();
    }
}
```

### 5. 설정 field와 생성자

- `createRefreshTokenCookie` 호출 위치: `SessionController.createSession`, `SessionController.refreshSession`.
- `ResponseCookie`를 반환한 것만으로 브라우저에 저장되지는 않는다. Controller가 `Set-Cookie` header로 응답해야 한다.
- `createExpiredRefreshTokenCookie` 호출 위치: `SessionController.deleteSession`.
- 이 메서드는 서버의 기존 Cookie 객체를 수정하지 않는다. 같은 이름·path의 Cookie를 `Max-Age=0`으로 만료시키라는 응답을 새로 만든다.
- DB의 AuthSession revoke는 별도로 `SessionService.deleteSession`에서 실행된다.

### 6. createRefreshTokenCookie의 실행 흐름

`SessionController.createSession`과 `SessionController.refreshSession`이 호출한다. 반환된 ResponseCookie는 Controller가 Set-Cookie header로 변환해야 브라우저에 저장된다.

### 7. createExpiredRefreshTokenCookie의 실행 흐름

`SessionController.deleteSession`이 호출한다. 같은 이름과 path의 Cookie에 Max-Age=0을 설정해 브라우저가 기존 Cookie를 삭제하도록 지시한다.

## 3. SessionRefreshResponseDto

파일: `/Users/miles/Documents/GitHub/KTB4_Miles_Week12_Back/src/main/java/kr/adapterz/springdatajpa/dto/user/SessionRefreshResponseDto.java`

### 전체 주석본

```java
package kr.adapterz.springdatajpa.dto.user;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter // getter를 생성한다.
@NoArgsConstructor // 기본 생성자를 생성한다.
public class SessionRefreshResponseDto {
    private String message; // 성공 message다.
    private String accessToken; // JSON body에 포함할 새 Access Token이다.

    @JsonIgnore // JSON body에서 제외한다.
    private String refreshToken; // Controller가 Cookie를 만들 때만 사용한다.

    public SessionRefreshResponseDto( // Service가 새 token 두 개를 전달한다.
            String accessToken,
            String refreshToken
    ) {
        this.message = "refresh_success"; // 성공 code를 설정한다.
        this.accessToken = accessToken; // body용 token을 저장한다.
        this.refreshToken = refreshToken; // Cookie용 원문을 저장한다.
    }
}
```

### 목차 주석본

```java
package kr.adapterz.springdatajpa.dto.user;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Getter;
import lombok.NoArgsConstructor;

//8
@Getter
@NoArgsConstructor
public class SessionRefreshResponseDto {
    private String message;
    private String accessToken;
    @JsonIgnore
    private String refreshToken;

    public SessionRefreshResponseDto(
            String accessToken,
            String refreshToken
    ) {
        this.message = "refresh_success";
        this.accessToken = accessToken;
        this.refreshToken = refreshToken;
    }
}
```

### 8. SessionRefreshResponseDto의 값 흐름

- `SessionService.refreshSession`이 이 DTO를 반환한다.
- `accessToken`은 JSON body로 직렬화된다.
- `refreshToken`은 `@JsonIgnore` 때문에 body에는 나오지 않고, `SessionController`가 Cookie를 만드는 데만 사용한다.

`SessionService.refreshSession`이 새 Access Token과 원문 Refresh Token을 생성자에 전달한다. Access Token은 JSON body로 직렬화되고, Refresh Token은 Controller가 Cookie를 만들 때만 읽는다.

---

## AuthSession.java

- 실제 파일: `/Users/miles/Documents/GitHub/KTB4_Miles_Week12_Back/src/main/java/kr/adapterz/springdatajpa/entity/AuthSession.java`

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

---

## AuthSessionRepository.java

- 실제 파일: `/Users/miles/Documents/GitHub/KTB4_Miles_Week12_Back/src/main/java/kr/adapterz/springdatajpa/repository/AuthSessionRepository.java`

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

---

## SessionService.java — refreshSession 연결

전체 Service 원문은 흐름 1 문서에 있다. 이 문서에서는 다음 상세 설명을 해당 method의 실행 흐름으로 읽는다.

### 4. `refreshSession` 재발급 흐름

호출 파일: `/Users/miles/Documents/GitHub/KTB4_Miles_Week12_Back/src/main/java/kr/adapterz/springdatajpa/controller/SessionController.java`

호출 메서드: `refreshSession`

매개변수: `@CookieValue`로 전달된 원문 Refresh Token

1. Cookie가 없으면 즉시 `Invalid_Refresh_Token` 예외를 던진다.
2. 원문을 hash로 바꾼다.
3. `findByRefreshTokenHash`가 비관적 쓰기 잠금으로 AuthSession을 조회한다.
4. row가 없으면 예외를 던진다.
5. `isActive`로 revoke 여부와 만료 여부를 확인한다.
6. 연결된 User가 삭제·정지 상태인지 확인한다.
7. 새 원문과 새 hash를 만든다.
8. `rotate`로 같은 AuthSession의 hash와 만료 시각을 변경한다.
9. 사용자 ID·authVersion으로 새 Access Token을 만든다.
10. 새 Access Token과 새 원문 Refresh Token을 DTO로 반환한다.

새 원문은 Controller가 Cookie에 넣고, DTO의 `@JsonIgnore` 때문에 JSON body에는 포함되지 않는다.

### 5. 같은 transaction이 필요한 이유

Refresh 흐름에서는 다음 작업이 하나의 원자적 범위에 있어야 한다.

```text
기존 세션 lock 조회
→ 활성 검사
→ rotate
→ 새 token 응답
→ transaction commit
```

`@Transactional`이 없으면 조회와 rotate가 서로 다른 DB 작업이 되어 동시 요청에서 같은 Refresh Token이 중복 사용될 가능성을 제어하기 어려워진다. Repository의 `PESSIMISTIC_WRITE`와 Service의 transaction이 함께 동시성 흐름을 구성한다.

---

## SessionController.java — refreshSession 연결

전체 Controller 원문은 흐름 1 문서에 있다. 여기서는 Cookie 입력·새 Set-Cookie header·JSON body 연결을 확인한다.

### 2. `refreshSession`의 각 코드가 실행되는 순서

실제 메서드:

```java
@PostMapping("/refresh")
public SessionRefreshResponseDto refreshSession(
        @CookieValue(
                name = RefreshCookieProvider.COOKIE_NAME,
                required = false
        ) String refreshToken,
        HttpServletResponse response
) {
    SessionRefreshResponseDto refreshResponse = sessionService.refreshSession(
            refreshToken
    );

    response.addHeader(
            HttpHeaders.SET_COOKIE,
            refreshCookieProvider
                    .createRefreshTokenCookie(refreshResponse.getRefreshToken())
                    .toString()
    );

    return refreshResponse;
}
```

#### 2-1. Cookie를 매개변수로 받는 과정

```java
@CookieValue(
        name = RefreshCookieProvider.COOKIE_NAME,
        required = false
) String refreshToken
```

Spring MVC는 요청의 Cookie 목록에서 `refreshToken`이라는 이름을 찾는다.

```http
Cookie: refreshToken=abc123
```

찾은 값만 문자열 매개변수에 넣는다.

```text
Cookie 이름: refreshToken
Cookie 값: abc123
→ String refreshToken = "abc123"
```

`required = false`는 Cookie가 없을 때 Spring이 Controller 진입 전 즉시 오류를 만들지 않게 한다. 대신 `refreshToken`에 null을 넣어 Service가 프로젝트의 `Invalid_Refresh_Token` 처리 흐름을 실행하게 한다.

#### 2-2. Service 반환값과 새 Cookie

```java
SessionRefreshResponseDto refreshResponse =
        sessionService.refreshSession(refreshToken);
```

Service 내부의 반환은 다음 순서로 만들어진다.

```text
원래 Cookie 원문
→ hash 계산
→ AuthSessionRepository의 lock 조회
→ isActive 검사
→ User 삭제·정지 검사
→ 새 원문·새 hash 생성
→ authSession.rotate(...)
→ 새 Access Token 생성
→ SessionRefreshResponseDto 반환
```

그 반환 객체에는 두 값이 있지만 사용처가 다르다.

```java
refreshResponse.getAccessToken()
```

은 JSON body 직렬화에 사용된다.

```java
refreshResponse.getRefreshToken()
```

은 새 Cookie를 만드는 데 사용된다.

#### 2-3. 재발급된 Cookie 응답

```java
refreshCookieProvider
        .createRefreshTokenCookie(refreshResponse.getRefreshToken())
        .toString()
```

기존 Cookie를 수정하는 것이 아니라 새 `Set-Cookie` 지시를 만든다. 같은 Cookie 이름과 path를 사용하므로 브라우저는 기존 값을 새 값으로 교체한다.
