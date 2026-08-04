# 4단계-2. Refresh Token과 Cookie

## 전체 흐름

```text
SessionService.createSession
→ RefreshTokenProvider.createRefreshToken
→ hashRefreshToken
→ AuthSession에 hash 저장
→ SessionController가 원문을 Cookie로 응답
```

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

## 다음 파일

- `entity/AuthSession.java`
- `repository/AuthSessionRepository.java`
- `service/SessionService.java`의 Refresh 흐름

현재 파일 진행률: **27개 확인 완료 / 최소 학습 대상 213개 = 약 12.7%**
