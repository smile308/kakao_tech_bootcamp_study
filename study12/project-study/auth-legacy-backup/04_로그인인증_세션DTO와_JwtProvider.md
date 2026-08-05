# 4단계-1. 로그인 입력·응답 DTO와 Access Token 생성/검증

## 이 묶음의 실행 흐름

```text
로그인 HTTP body
→ SessionRequestDto 역직렬화
→ validation annotation 검사
→ SessionService가 JwtProvider.createAccessToken 호출
→ JWT 문자열 생성
→ SessionResponseDto에 accessToken 저장
→ HTTP JSON 응답
```

이번 묶음은 로그인 전체가 아니라 로그인에 사용하는 DTO와 Access Token 도구만 다룬다. 실제 사용자 조회·비밀번호 비교·Refresh Token 저장은 다음 인증 묶음에서 확인한다.

## 1. SessionRequestDto

파일: `/Users/miles/Documents/GitHub/KTB4_Miles_Week12_Back/src/main/java/kr/adapterz/springdatajpa/dto/user/SessionRequestDto.java`

### 전체 주석본

```java
package kr.adapterz.springdatajpa.dto.user;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter // private field를 읽는 getter를 Lombok이 생성한다.
@NoArgsConstructor // Jackson이 HTTP JSON을 객체로 만들 때 사용할 기본 생성자를 생성한다.
public class SessionRequestDto { // 로그인 요청 JSON의 구조를 표현하는 DTO다.
    @NotBlank // null·빈 문자열·공백 문자열을 허용하지 않는다.
    @Email // 문자열이 이메일 형식인지 검사한다.
    private String email; // HTTP body의 email 값이 저장된다.

    @NotBlank // 비밀번호가 비어 있지 않은지 검사한다.
    @Pattern( // 정규식 조건을 만족하는지 검사한다.
            regexp = "^(?=.*[A-Z])(?=.*[a-z])(?=.*\\d)(?=.*[!@#$%^&*()_+\\-=\\[\\]{};':\"\\\\|,.<>\\/?~]).{8,20}$" // 대·소문자·숫자·특수문자를 포함한 8~20자 조건이다.
    )
    private String password; // HTTP body의 password 값이 저장된다.
}
```

### 목차 주석본

```java
package kr.adapterz.springdatajpa.dto.user;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.NoArgsConstructor;

//1
@Getter
@NoArgsConstructor
public class SessionRequestDto {
    @NotBlank
    @Email
    private String email;

    @NotBlank
    @Pattern(
            regexp = "^(?=.*[A-Z])(?=.*[a-z])(?=.*\\d)(?=.*[!@#$%^&*()_+\\-=\\[\\]{};':\"\\\\|,.<>\\/?~]).{8,20}$"
    )
    private String password;
}
```

`SessionRequestDto`는 응답 DTO가 아니라 로그인 요청을 담는 입력 DTO다. Controller의 `@RequestBody`가 JSON을 이 객체로 만들고, `@Valid`가 붙어 있을 때 annotation 검사가 실행된다. 이 DTO 자체는 사용자 조회나 로그인 판단을 하지 않는다.

처음 등장한 문법:

- annotation: `@Email`처럼 class·field에 metadata를 붙인다.
- Lombok annotation: 실제 getter와 기본 생성자 코드를 컴파일 시 생성한다.
- `regexp`: 정규식 문자열로 입력 형식 조건을 표현한다.

## 2. SessionResponseDto

파일: `/Users/miles/Documents/GitHub/KTB4_Miles_Week12_Back/src/main/java/kr/adapterz/springdatajpa/dto/user/SessionResponseDto.java`

### 전체 주석본

```java
package kr.adapterz.springdatajpa.dto.user;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter // 응답 JSON을 만들 getter를 생성한다.
@NoArgsConstructor // Jackson 역직렬화와 프레임워크 요구를 위해 기본 생성자를 생성한다.
public class SessionResponseDto { // 로그인 성공 응답의 구조다.
    private String message; // 로그인 결과 code를 담는다.
    private String accessToken; // 클라이언트가 이후 Authorization header에 사용할 Access Token이다.

    @JsonIgnore // getter가 있어도 JSON response body에는 이 field를 포함하지 않는다.
    private String refreshToken; // 서버가 Cookie를 만들 때 내부적으로 전달받는 Refresh Token이다.

    private Long userId; // 로그인한 사용자의 ID를 응답에 담는다.

    public SessionResponseDto( // Service가 성공 결과를 DTO로 조립하는 생성자다.
            String accessToken, // 새로 발급한 Access Token이다.
            String refreshToken, // Cookie로 옮길 Refresh Token 원문이다.
            Long userId // 인증된 사용자 ID다.
    ) {
        this.message = "login_success"; // 고정 성공 code를 설정한다.
        this.accessToken = accessToken; // Access Token을 저장한다.
        this.refreshToken = refreshToken; // 내부 field에만 Refresh Token을 저장한다.
        this.userId = userId; // 사용자 ID를 저장한다.
    }
}
```

### 목차 주석본

```java
package kr.adapterz.springdatajpa.dto.user;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Getter;
import lombok.NoArgsConstructor;

//2
@Getter
@NoArgsConstructor
public class SessionResponseDto {
    private String message;
    private String accessToken;

    @JsonIgnore
    private String refreshToken;

    private Long userId;

    public SessionResponseDto(
            String accessToken,
            String refreshToken,
            Long userId
    ) {
        this.message = "login_success";
        this.accessToken = accessToken;
        this.refreshToken = refreshToken;
        this.userId = userId;
    }
}
```

`@JsonIgnore` 때문에 `refreshToken` field는 DTO 내부에는 있지만 Jackson이 JSON body로 직렬화할 때 제외된다. 실제 브라우저 전달은 SessionController가 ResponseCookie를 별도로 response header에 추가하는 흐름에서 확인한다. 따라서 이 DTO만 보고 Refresh Token이 body로 전송된다고 해석하면 안 된다.

## 3. JwtProvider 선언과 설정 주입

파일: `/Users/miles/Documents/GitHub/KTB4_Miles_Week12_Back/src/main/java/kr/adapterz/springdatajpa/auth/JwtProvider.java`

### 전체 주석본

```java
package kr.adapterz.springdatajpa.auth;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import kr.adapterz.springdatajpa.exception.AuthException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

@Component // Spring이 JwtProvider 객체를 Bean으로 생성하고 주입 대상으로 등록한다.
public class JwtProvider { // Access Token 생성과 검증을 담당한다.
    private static final String AUTH_VERSION_CLAIM = "authVersion"; // 토큰 안에 인증 버전 값을 저장할 claim 이름이다.

    private final SecretKey secretKey; // 서명과 검증에 같은 비밀키를 사용한다.
    private final long accessExpirationMillis; // Access Token 유효기간을 밀리초로 저장한다.

    public JwtProvider( // application 설정값을 받아 Provider를 초기화한다.
            @Value("${jwt.secret}") String secret, // YAML의 jwt.secret 값이다.
            @Value("${jwt.access-expiration-millis}") long accessExpirationMillis // YAML의 Access Token 만료 시간이다.
    ) {
        this.secretKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8)); // 문자열 secret을 HMAC 서명용 SecretKey로 변환한다.
        this.accessExpirationMillis = accessExpirationMillis; // 설정값을 field에 저장한다.
    }

    public String createAccessToken(Long userId, long authVersion) { // 인증된 사용자의 Access Token 문자열을 만든다.
        Date now = new Date(); // 발급 시각을 현재 시각으로 만든다.
        Date expiration = new Date(now.getTime() + accessExpirationMillis); // 발급 시각에 유효기간을 더해 만료 시각을 계산한다.

        return Jwts.builder() // JWT builder를 시작한다.
                .subject(String.valueOf(userId)) // subject에 사용자 ID를 문자열로 저장한다.
                .claim(AUTH_VERSION_CLAIM, authVersion) // 사용자 인증 버전을 custom claim으로 저장한다.
                .issuedAt(now) // 발급 시각을 기록한다.
                .expiration(expiration) // 만료 시각을 기록한다.
                .signWith(secretKey) // 비밀키로 JWT에 서명한다.
                .compact(); // builder 결과를 전송 가능한 문자열로 직렬화한다.
    }

    public AccessTokenClaims getAccessTokenClaims(String token) { // 전달받은 JWT를 검증하고 필요한 claim을 반환한다.
        try { // JWT parsing 과정의 라이브러리 예외를 프로젝트 예외로 변환할 범위다.
            Claims claims = Jwts.parser() // JWT parser를 시작한다.
                    .verifyWith(secretKey) // 같은 비밀키로 서명을 검증하도록 설정한다.
                    .build() // parser를 완성한다.
                    .parseSignedClaims(token) // 서명된 token을 파싱한다.
                    .getPayload(); // 검증된 payload claim을 꺼낸다.

            Object authVersionClaim = claims.get(AUTH_VERSION_CLAIM); // authVersion claim을 아직 일반 Object로 꺼낸다.
            if (!(authVersionClaim instanceof Number authVersion)) { // 값이 숫자가 아니면 유효한 토큰으로 보지 않는다.
                throw new AuthException("Invalid_Token"); // 인증 실패 예외를 발생시킨다.
            }

            return new AccessTokenClaims( // 검증된 subject와 authVersion을 전용 결과 객체로 묶는다.
                    Long.valueOf(claims.getSubject()), // subject 문자열을 사용자 ID Long으로 변환한다.
                    authVersion.longValue() // Number claim을 long 값으로 변환한다.
            );
        } catch (JwtException | IllegalArgumentException e) { // 서명·형식·변환 관련 예외를 한 번에 잡는다.
            throw new AuthException("Invalid_Token"); // 외부에는 내부 라이브러리 예외 대신 동일한 인증 오류를 전달한다.
        }
    }
}
```

### 목차 주석본

```java
package kr.adapterz.springdatajpa.auth;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import kr.adapterz.springdatajpa.exception.AuthException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

//3
@Component
public class JwtProvider {
    private static final String AUTH_VERSION_CLAIM = "authVersion";

    private final SecretKey secretKey;
    private final long accessExpirationMillis;

    //3
    public JwtProvider(
            @Value("${jwt.secret}") String secret,
            @Value("${jwt.access-expiration-millis}") long accessExpirationMillis
    ) {
        this.secretKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.accessExpirationMillis = accessExpirationMillis;
    }

    //4
    public String createAccessToken(Long userId, long authVersion) {
        Date now = new Date();
        Date expiration = new Date(now.getTime() + accessExpirationMillis);

        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim(AUTH_VERSION_CLAIM, authVersion)
                .issuedAt(now)
                .expiration(expiration)
                .signWith(secretKey)
                .compact();
    }

    //4
    public AccessTokenClaims getAccessTokenClaims(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(secretKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();

            Object authVersionClaim = claims.get(AUTH_VERSION_CLAIM);
            if (!(authVersionClaim instanceof Number authVersion)) {
                throw new AuthException("Invalid_Token");
            }

            return new AccessTokenClaims(
                    Long.valueOf(claims.getSubject()),
                    authVersion.longValue()
            );
        } catch (JwtException | IllegalArgumentException e) {
            throw new AuthException("Invalid_Token");
        }
    }
}
```

## 4. AccessTokenClaims — 검증 결과를 담는 record

파일: `/Users/miles/Documents/GitHub/KTB4_Miles_Week12_Back/src/main/java/kr/adapterz/springdatajpa/auth/AccessTokenClaims.java`

### 전체 주석본

```java
package kr.adapterz.springdatajpa.auth;

public record AccessTokenClaims( // JWT에서 꺼낸 인증 정보를 하나의 불변 데이터 객체로 묶는다.
        Long userId, // JWT subject에서 변환한 사용자 ID다.
        long authVersion // JWT custom claim에서 꺼낸 인증 버전이다.
) {
}
```

### 목차 주석본

```java
package kr.adapterz.springdatajpa.auth;

//4
public record AccessTokenClaims(
        Long userId,
        long authVersion
) {
}
```

`record`는 데이터를 보관하는 간단한 객체를 선언하는 Java 문법이다. 위 선언으로 `userId()`와 `authVersion()` 접근 메서드, 생성자, `equals`, `hashCode`, `toString`이 자동으로 제공된다. `JwtProvider.getAccessTokenClaims()`가 이 객체를 반환하고, `JwtAuthenticationFilter`가 다음처럼 값을 읽는다.

```java
AccessTokenClaims tokenClaims = jwtProvider.getAccessTokenClaims(token);
tokenClaims.userId();
tokenClaims.authVersion();
```

### 호출·반환 흐름

```text
JwtAuthenticationFilter
→ JwtProvider.getAccessTokenClaims(token)
→ new AccessTokenClaims(userId, authVersion)
→ JwtAuthenticationFilter가 두 값으로 사용자와 인증 버전을 확인
```

## 5. JwtProvider의 실제 호출 관계

현재 코드에서 확인할 호출 위치는 다음과 같다.

- `SessionService`: 로그인 성공 시 `createAccessToken(userId, authVersion)` 호출
- `SessionService`: Refresh Token 재발급 시에도 `createAccessToken(...)` 호출
- `JwtAuthenticationFilter`: Authorization header에서 얻은 문자열에 `getAccessTokenClaims(token)` 호출

`createAccessToken`의 반환값은 문자열이며, 로그인 응답 DTO의 `accessToken` field와 인증 응답에 사용된다. `getAccessTokenClaims`의 반환값은 `JwtAuthenticationFilter`가 사용자 ID와 `authVersion`을 읽어 현재 사용자의 인증 상태를 확인하는 데 사용한다.

## 6. 이 묶음에서 처음 나온 문법

- `@Value("${...}")`: Spring 설정 키의 값을 생성자 매개변수에 주입한다.
- `final`: 생성 후 다른 객체로 교체하지 않을 field를 선언한다. 내부 객체의 모든 상태가 불변이라는 뜻은 아니다.
- `instanceof Number authVersion`: 타입 검사와 동시에 해당 타입의 지역 변수로 바인딩한다.
- `try-catch`: 라이브러리 예외를 프로젝트의 `AuthException`으로 바꾼다.
- method chaining: `builder().subject().claim().compact()`처럼 앞 메서드의 반환 객체에 다음 메서드를 이어 호출한다.

## 다음에 읽을 파일

다음 묶음에서는 Refresh Token의 생성·hash·Cookie 변환을 확인한다.

- `auth/RefreshTokenProvider.java`
- `auth/RefreshCookieProvider.java`
- `dto/user/SessionRefreshResponseDto.java`

## 현재 진행 위치

인증 단계의 첫 번째 파일 묶음까지 완료했다.

파일 기준 진행률: **24개 확인 완료 / 최소 학습 대상 213개 = 약 11.3%**

`authApi`와 `api.js`는 이번 집계에서 전체 파일을 정독하지 않았으므로 제외했다.
