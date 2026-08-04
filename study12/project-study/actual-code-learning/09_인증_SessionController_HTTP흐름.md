# 4단계-6. SessionController와 HTTP Cookie 흐름

## 파일 위치와 책임

파일: `/Users/miles/Documents/GitHub/KTB4_Miles_Week12_Back/src/main/java/kr/adapterz/springdatajpa/controller/SessionController.java`

이 Controller는 로그인·Refresh·로그아웃 HTTP 요청을 받아 `SessionService`에 위임하고, Refresh Token은 JSON body가 아니라 Cookie header로 응답한다.

## 전체 코드와 설명 주석

```java
package kr.adapterz.springdatajpa.controller;

import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import kr.adapterz.springdatajpa.auth.RefreshCookieProvider;
import kr.adapterz.springdatajpa.dto.user.SessionRefreshResponseDto;
import kr.adapterz.springdatajpa.dto.user.SessionRequestDto;
import kr.adapterz.springdatajpa.dto.user.SessionResponseDto;
import kr.adapterz.springdatajpa.service.SessionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

/*
 * 1. Controller 기본 설정
 * @RestController는 반환 객체를 JSON response body로 직렬화한다.
 * @RequestMapping("/sessions")는 아래 모든 endpoint 앞에 /sessions를 붙인다.
 * @RequiredArgsConstructor는 final 의존성을 받는 생성자를 생성한다.
 */
@RestController // Spring MVC Controller로 등록하고 반환 객체를 JSON response body로 직렬화한다.
@RequestMapping("/sessions") // 이 class의 모든 endpoint 앞에 /sessions를 붙인다.
@RequiredArgsConstructor // final field를 받는 생성자를 Lombok이 만들고 Spring이 의존성을 주입하게 한다.
public class SessionController {
    private final SessionService sessionService; // Controller가 직접 인증·DB 작업을 하지 않고 업무를 위임할 Service Bean이다.
    private final RefreshCookieProvider refreshCookieProvider; // Cookie 속성을 조립하는 Provider Bean이다.

    /*
     * 2. 로그인 endpoint
     * POST /sessions 요청이 들어오면 실행된다.
     * @Valid가 DTO annotation 검증을 실행하고,
     * Service가 반환한 Refresh Token은 Cookie header로 옮긴다.
     */
    @PostMapping // 기본 경로 /sessions에 POST를 연결한다.
    @ResponseStatus(HttpStatus.CREATED) // 성공 시 HTTP 201을 기본 상태로 설정한다.
    public SessionResponseDto createSession( // JSON body와 response header를 함께 처리한다.
            @Valid @RequestBody SessionRequestDto request, // JSON body를 DTO로 역직렬화하고 validation을 실행한다.
            HttpServletResponse response // Spring이 현재 HTTP 요청에 연결해 전달한 응답 객체이며 header·status·body의 바탕이다.
    ){
        SessionResponseDto sessionResponse = sessionService.createSession(request); // 인증과 token 생성을 Service에 위임한다.

        response.addHeader( // response 객체의 header 목록을 수정하며, Controller 반환 body와 함께 나중에 브라우저로 전송된다.
                HttpHeaders.SET_COOKIE, // Spring HttpHeaders의 "Set-Cookie" 상수이며, 서버가 브라우저에 Cookie 저장·교체·삭제를 지시하는 응답 header 이름이다.
                refreshCookieProvider // Cookie 설정을 만드는 Provider 객체다.
                        .createRefreshTokenCookie(sessionResponse.getRefreshToken()) // DTO의 원문으로 ResponseCookie 객체를 만든다.
                        .toString() // ResponseCookie를 Set-Cookie header에 넣을 문자열로 바꾼다.
        );

        return sessionResponse; // Controller 메서드가 끝나면 DispatcherServlet이 반환 객체를 Jackson에 전달해 JSON body로 쓰고, 같은 HttpServletResponse 객체에 앞서 저장된 Set-Cookie header와 body를 함께 기록해 Servlet 응답을 완료한다.
    }

    /*
     * 3. Refresh endpoint
     * POST /sessions/refresh 요청의 Cookie를 읽는다.
     * Service가 기존 token을 검증하고 새 DTO를 반환하면,
     * 새 Refresh Token을 다시 Set-Cookie header에 넣는다.
     */
    @PostMapping("/refresh") // /sessions/refresh에 POST를 연결한다.
    public SessionRefreshResponseDto refreshSession( // 새 Access Token 응답을 반환한다.
            @CookieValue( // 요청 Cookie에서 값을 꺼낸다.
                    name = RefreshCookieProvider.COOKIE_NAME, // Cookie 이름을 Provider 상수와 공유한다.
                    required = false // Cookie가 없어도 Controller 진입을 허용한다.
            ) String refreshToken, // Cookie 원문이 없으면 null이 될 수 있다.
            HttpServletResponse response // 현재 HTTP 응답 객체이며 새 Set-Cookie header를 등록할 대상이다.
    ) {
        SessionRefreshResponseDto refreshResponse = sessionService.refreshSession( // 검증·rotate를 Service에 위임한다.
                refreshToken // Cookie에서 읽은 원문을 전달한다.
        );

        response.addHeader( // response 객체에 새 header를 등록하고 Controller return 시 body와 함께 전송되게 한다.
                HttpHeaders.SET_COOKIE, // 브라우저에 기존 Cookie를 새 값으로 교체하라고 지시하는 "Set-Cookie" header 이름이다.
                refreshCookieProvider
                        .createRefreshTokenCookie(refreshResponse.getRefreshToken()) // 새 원문으로 Cookie를 만든다.
                        .toString()
        );

        return refreshResponse; // Controller 반환 후 DispatcherServlet이 Jackson으로 accessToken·message를 JSON body에 쓰며, 이미 같은 response 객체에 등록한 Set-Cookie header도 유지한 채 HTTP 응답을 commit한다.
    }

    /*
     * 4. 로그아웃 endpoint
     * POST가 아니라 DELETE /sessions 요청을 처리한다.
     * DB AuthSession revoke는 Service가 실행하고,
     * 브라우저 Cookie 삭제 지시는 Controller가 response header로 보낸다.
     */
    @DeleteMapping // /sessions에 DELETE를 연결한다.
    @ResponseStatus(HttpStatus.NO_CONTENT) // 성공 시 body 없는 HTTP 204를 반환한다.
    public void deleteSession( // 반환 body 없이 로그아웃을 처리한다.
            @CookieValue( // 요청 Cookie에서 기존 Refresh Token을 읽는다.
                    name = RefreshCookieProvider.COOKIE_NAME, // 로그인·Refresh와 같은 Cookie 이름이다.
                    required = false // Cookie가 없어도 로그아웃 endpoint에 들어올 수 있다.
            ) String refreshToken, // 없으면 null이며 Service가 정상 종료할 수 있다.
            HttpServletResponse response // 현재 HTTP 응답 객체이며 만료 지시 header를 등록할 대상이다.
    ){
        sessionService.deleteSession(refreshToken); // DB AuthSession revoke를 Service에 위임한다.

        response.addHeader( // response 객체에 삭제 지시 header를 등록하고 204 응답과 함께 전송되게 한다.
                HttpHeaders.SET_COOKIE, // 브라우저에 같은 Cookie를 Max-Age=0으로 삭제하라고 지시하는 "Set-Cookie" header 이름이다.
                refreshCookieProvider
                        .createExpiredRefreshTokenCookie() // Max-Age=0인 Cookie를 만든다.
                        .toString()
        );
    }
}
```

## 코드 바로 아래 상세 설명

### 1. Controller annotation과 의존성

- `@RestController`: Controller 메서드 반환값을 view 이름이 아니라 JSON body로 처리한다.
- `@RequestMapping("/sessions")`: `@PostMapping`의 빈 경로를 `/sessions`, `@PostMapping("/refresh")`를 `/sessions/refresh`로 만든다.
- `@RequiredArgsConstructor`: 두 `final` field를 생성자 매개변수로 받아 Spring이 주입하게 한다.
- `SessionService`: 인증 업무와 DB 변경을 담당한다.
- `RefreshCookieProvider`: Cookie 속성을 조립한다.

### 2. `createSession` 호출·반환 흐름

요청:

```http
POST /sessions
Content-Type: application/json
```

1. JSON body가 `SessionRequestDto`로 역직렬화된다.
2. `@Valid`가 email·password annotation을 검사한다.
3. `sessionService.createSession(request)`이 인증과 token 생성을 실행한다.
4. Service가 반환한 DTO의 `refreshToken`은 JSON body가 아니라 Cookie 생성에 사용된다.
5. `return sessionResponse`의 나머지 field가 JSON body가 된다.

`@ResponseStatus(HttpStatus.CREATED)` 때문에 성공 상태는 201이다. Service 예외가 발생하면 정상 return까지 오지 않고 `GlobalExceptionHandler`로 이동한다.

### 3. `refreshSession` Cookie 입력·출력 흐름

요청:

```http
POST /sessions/refresh
Cookie: refreshToken=원문값
```

`@CookieValue`는 request Cookie에서 `refreshToken` 값을 꺼낸다. `required = false`이므로 Cookie가 없으면 Spring이 즉시 400을 만들지 않고 `refreshToken = null`로 메서드에 진입시킨다. 이후 `SessionService.refreshSession`이 `Invalid_Refresh_Token`을 발생시킨다.

새 Refresh Token은 `Set-Cookie` header로 다시 저장되고, 새 Access Token은 반환 DTO를 통해 JSON body로 전송된다.

### 4. `deleteSession` 로그아웃 흐름

요청:

```http
DELETE /sessions
Cookie: refreshToken=원문값
```

1. Service가 원문 hash로 AuthSession을 찾아 revoke한다.
2. Cookie Provider가 같은 이름·path에 `Max-Age=0`인 Cookie를 만든다.
3. 브라우저가 기존 Cookie를 삭제한다.
4. `void` 반환과 `204 No Content`로 body 없는 성공 응답을 보낸다.

Cookie가 없어도 `required = false`로 Controller에 진입할 수 있고, Service는 null을 확인한 뒤 종료한다.

## Controller 의존성 연결

### 1. `sessionService` 의존성

Controller는 로그인 판단이나 DB query를 직접 실행하지 않는다. 다음 field를 통해 Service Bean을 전달받는다.

```java
private final SessionService sessionService;
```

Lombok의 `@RequiredArgsConstructor`가 다음과 같은 생성자를 컴파일 시 만들어 준다고 이해하면 된다.

```java
public SessionController(
        SessionService sessionService,
        RefreshCookieProvider refreshCookieProvider
) {
    this.sessionService = sessionService;
    this.refreshCookieProvider = refreshCookieProvider;
}
```

Spring은 `SessionService`와 `RefreshCookieProvider` Bean을 찾아 이 생성자에 주입한다. 그래서 Controller 안에서 다음처럼 호출할 수 있다.

```java
sessionService.createSession(request);
sessionService.refreshSession(refreshToken);
sessionService.deleteSession(refreshToken);
```

각 메서드의 실제 반환값은 다음 Controller 변수로 전달된다.

```java
SessionResponseDto sessionResponse = sessionService.createSession(request);
SessionRefreshResponseDto refreshResponse = sessionService.refreshSession(refreshToken);
```

### 2. 로그인 endpoint의 전체 데이터 이동

관련 Service 코드 발췌:

```java
// SessionService.createSession
Authentication authentication = authenticationManager.authenticate(
        new UsernamePasswordAuthenticationToken(
                request.getEmail(),
                request.getPassword()
        )
);

String accessToken = jwtProvider.createAccessToken(
        userDetails.getUserId(),
        userDetails.getAuthVersion()
);
String refreshToken = refreshTokenProvider.createRefreshToken();
String refreshTokenHash = refreshTokenProvider.hashRefreshToken(refreshToken);

authSessionRepository.save(new AuthSession(
        userDetails.getUser(),
        refreshTokenHash,
        refreshTokenProvider.createExpirationTime()
));

return new SessionResponseDto(
        accessToken,
        refreshToken,
        userDetails.getUserId()
);
```

Controller와 Service 사이의 값 이동은 다음과 같다.

```text
HTTP JSON body
→ @RequestBody SessionRequestDto request
→ sessionService.createSession(request)
→ AuthenticationManager 인증
→ Access Token·Refresh Token 생성
→ AuthSession DB 저장
→ SessionResponseDto 반환
```

그 다음 Controller가 반환 DTO에서 원문 Refresh Token만 꺼낸다.

```java
sessionResponse.getRefreshToken()
```

이 값은 `@JsonIgnore`가 적용된 field이므로 JSON body에는 들어가지 않고 Cookie 생성에만 사용된다.

### 3. 로그인 응답이 두 곳으로 나뉘는 이유

로그인 성공 결과는 두 경로로 나뉜다.

```text
SessionResponseDto.accessToken
→ return 값
→ Jackson
→ JSON response body
```

```text
SessionResponseDto.refreshToken
→ RefreshCookieProvider.createRefreshTokenCookie
→ ResponseCookie.toString()
→ Set-Cookie response header
```

Access Token은 JavaScript의 API 요청에서 Authorization header로 사용해야 하므로 body로 전달된다. Refresh Token은 JavaScript가 직접 읽지 못하도록 HttpOnly Cookie로 전달한다.

### 4. Refresh endpoint의 입력과 출력 연결

Controller의 입력 부분:

```java
@CookieValue(
        name = RefreshCookieProvider.COOKIE_NAME,
        required = false
) String refreshToken
```

이 annotation은 다음 HTTP header에서 값을 찾는다.

```http
Cookie: refreshToken=abc123
```

찾은 문자열 `abc123`이 `refreshToken` 매개변수에 들어간다. Cookie가 없으면 `required = false` 때문에 null이 들어가고, Service의 다음 조건으로 이동한다.

```java
if (refreshToken == null || refreshToken.isBlank()) {
    throw new AuthException("Invalid_Refresh_Token");
}
```

Refresh Service의 반환값은 다시 Controller로 온다.

```java
SessionRefreshResponseDto refreshResponse =
        sessionService.refreshSession(refreshToken);
```

그 안에서:

```java
authSession.rotate(newRefreshTokenHash, newExpirationTime);
return new SessionRefreshResponseDto(accessToken, newRefreshToken);
```

Controller는 반환 DTO를 두 방향으로 나눈다.

```text
refreshResponse.accessToken
→ JSON body

refreshResponse.refreshToken
→ 새 Set-Cookie header
```

### 5. 로그아웃 endpoint의 두 가지 삭제

로그아웃은 브라우저와 DB를 각각 처리해야 한다.

```java
sessionService.deleteSession(refreshToken);
```

Service 내부에서는:

```java
String refreshTokenHash = refreshTokenProvider.hashRefreshToken(refreshToken);
authSessionRepository
        .findByRefreshTokenHash(refreshTokenHash)
        .ifPresent(authSession -> authSession.revoke(LocalDateTime.now()));
```

이 코드는 DB AuthSession을 revoke한다. 이후 Controller는 다음을 응답한다.

```java
refreshCookieProvider
        .createExpiredRefreshTokenCookie()
        .toString()
```

이 코드는 브라우저의 Cookie를 삭제하도록 `Set-Cookie` header를 만든다.

```text
DB AuthSession revoke
+
브라우저 Cookie 만료
→ 로그아웃 완료
```

둘 중 하나만 하면 문제가 생긴다.

- Cookie만 삭제: DB 세션이 여전히 유효할 수 있다.
- DB만 revoke: 브라우저에 남은 Cookie가 계속 전송된다.

## Controller annotation 문법 상세

## 실제 메서드별 줄 단위 실행 해설

### 1. `createSession`의 각 코드가 실행되는 순서

실제 메서드:

```java
@PostMapping
@ResponseStatus(HttpStatus.CREATED)
public SessionResponseDto createSession(
        @Valid @RequestBody SessionRequestDto request,
        HttpServletResponse response
){
    SessionResponseDto sessionResponse = sessionService.createSession(request);

    response.addHeader(
            HttpHeaders.SET_COOKIE,
            refreshCookieProvider
                    .createRefreshTokenCookie(sessionResponse.getRefreshToken())
                    .toString()
    );

    return sessionResponse;
}
```

#### 1-1. 요청이 메서드에 도착하기 전

```java
@PostMapping
```

class의 `@RequestMapping("/sessions")`와 합쳐져 다음 요청을 이 메서드에 연결한다.

```text
POST /sessions
```

```java
@ResponseStatus(HttpStatus.CREATED)
```

메서드가 정상적으로 return하면 Spring MVC가 HTTP 상태를 201로 설정한다. 이 annotation은 Service의 반환 DTO를 만드는 기능이 아니라, Controller의 정상 응답 상태만 지정한다.

#### 1-2. JSON을 Java 객체로 만드는 부분

```java
@Valid @RequestBody SessionRequestDto request
```

실행 순서는 다음과 같다.

```text
HTTP body JSON
→ Jackson이 SessionRequestDto 생성
→ @NotBlank·@Email·@Pattern 검사
→ 검사 성공 시 request 변수에 저장
→ 검사 실패 시 Controller method 본문 실행 전 예외
```

`request`는 Controller가 새로 만든 임의 객체가 아니라, 요청 body에서 역직렬화된 객체다. `request.getEmail()`과 `request.getPassword()`의 값은 다음 Service 호출로 전달된다.

#### 1-3. Service 호출과 반환값 저장

```java
SessionResponseDto sessionResponse =
        sessionService.createSession(request);
```

왼쪽과 오른쪽의 역할은 다르다.

- 오른쪽: Service 메서드를 실행하고 로그인 업무를 시작한다.
- 왼쪽: Service가 반환한 `SessionResponseDto` 객체의 참조를 `sessionResponse` 변수에 저장한다.

Service 내부에서는 다음이 실행된다.

```java
Authentication authentication =
        authenticationManager.authenticate(...);
CustomUserDetails userDetails =
        (CustomUserDetails) authentication.getPrincipal();
String accessToken = jwtProvider.createAccessToken(...);
String refreshToken = refreshTokenProvider.createRefreshToken();
String refreshTokenHash = refreshTokenProvider.hashRefreshToken(refreshToken);
authSessionRepository.save(new AuthSession(...));
return new SessionResponseDto(accessToken, refreshToken, userDetails.getUserId());
```

따라서 Controller가 Service를 호출하기 전에는 `sessionResponse`가 존재하지 않고, Service의 마지막 `return`이 실행된 뒤에만 값이 들어온다.

#### 1-4. Refresh Token을 header로 옮기는 부분

```java
response.addHeader(
        HttpHeaders.SET_COOKIE,
        refreshCookieProvider
                .createRefreshTokenCookie(sessionResponse.getRefreshToken())
                .toString()
);
```

안쪽부터 실행된다.

1. `sessionResponse.getRefreshToken()`이 DTO 내부의 원문 Refresh Token을 꺼낸다.
2. `createRefreshTokenCookie(...)`가 Cookie 속성을 설정한 `ResponseCookie` 객체를 만든다.
3. `.toString()`이 Cookie 객체를 HTTP header 문자열로 바꾼다.
4. `response.addHeader`가 응답에 `Set-Cookie` header를 추가한다.

`refreshToken` field는 DTO에 있지만 `@JsonIgnore`가 붙어 있으므로 body 직렬화에서는 제외된다. 따라서 이 header 추가 코드가 없으면 브라우저가 Refresh Token을 Cookie로 받지 못한다.

#### 1-5. Controller의 최종 반환

```java
return sessionResponse;
```

`@RestController` 때문에 이 객체는 view 이름으로 해석되지 않고 Jackson에 전달된다.

```text
SessionResponseDto
→ getter로 message/accessToken/userId 읽기
→ JSON response body
```

동시에 앞에서 추가한 `Set-Cookie` header도 같은 HTTP 응답에 포함된다.

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

### 3. `deleteSession`의 각 코드가 실행되는 순서

실제 메서드:

```java
@DeleteMapping
@ResponseStatus(HttpStatus.NO_CONTENT)
public void deleteSession(
        @CookieValue(
                name = RefreshCookieProvider.COOKIE_NAME,
                required = false
        ) String refreshToken,
        HttpServletResponse response
){
    sessionService.deleteSession(refreshToken);

    response.addHeader(
            HttpHeaders.SET_COOKIE,
            refreshCookieProvider
                    .createExpiredRefreshTokenCookie()
                    .toString()
    );
}
```

#### 3-1. Service의 DB 무효화

```java
sessionService.deleteSession(refreshToken);
```

이 한 줄이 브라우저 Cookie를 지우는 것은 아니다. Service는 다음 DB 작업만 수행한다.

```text
원문 Refresh Token
→ hashRefreshToken
→ findByRefreshTokenHash
→ AuthSession.revoke(now)
→ transaction flush
→ revoked_at 저장
```

#### 3-2. 브라우저 Cookie 만료

```java
refreshCookieProvider
        .createExpiredRefreshTokenCookie()
        .toString()
```

이 값은 `Max-Age=0`인 `Set-Cookie` header가 된다. 브라우저는 같은 이름·path의 기존 Cookie를 삭제한다.

DB revoke와 Cookie 만료는 서로 다른 시스템의 상태 변경이다.

```text
Service → DB AuthSession 무효화
Controller → 브라우저 Cookie 삭제 지시
```

#### 3-3. `void`와 204 응답

```java
public void deleteSession(...)
```

이 메서드는 Java 객체를 반환하지 않는다. `@ResponseStatus(HttpStatus.NO_CONTENT)`가 HTTP 204를 지정하고, `addHeader`가 Cookie 삭제 header를 추가한다. 따라서 body 없이 header와 상태 코드만 있는 응답이 된다.

### `@PostMapping("/refresh")`

annotation에 경로를 넣으면 class의 기본 경로와 합쳐진다.

```java
@RequestMapping("/sessions")
@PostMapping("/refresh")
```

최종 HTTP 경로:

```text
POST /sessions/refresh
```

### `@RequestBody`

```java
@RequestBody SessionRequestDto request
```

Spring MVC가 다음 과정을 수행한다.

```text
HTTP JSON 문자열
→ Jackson 역직렬화
→ SessionRequestDto 객체
→ request 변수
```

### `@Valid`

```java
@Valid @RequestBody SessionRequestDto request
```

`@RequestBody`로 객체를 만든 뒤 DTO의 `@NotBlank`, `@Email`, `@Pattern` 조건을 검사한다. 실패하면 Controller method body에 들어오기 전에 `MethodArgumentNotValidException`이 발생하고 `GlobalExceptionHandler`로 이동한다.

### 반환 타입과 JSON 직렬화

```java
public SessionRefreshResponseDto refreshSession(...)
```

Controller가 객체를 반환하면 `@RestController`가 Jackson에 직렬화를 맡긴다.

```text
SessionRefreshResponseDto 객체
→ getter로 field 조회
→ JSON response body
```

단, `@JsonIgnore`가 붙은 `refreshToken`은 JSON에서 제외된다.

## 새 문법·개념

- `@RequestBody`: JSON body를 Java DTO로 역직렬화한다.
- `@CookieValue`: HTTP Cookie 값을 메서드 매개변수에 주입한다.
- `HttpServletResponse.addHeader`: Controller가 응답 header를 직접 추가한다.
- `@ResponseStatus`: 메서드의 기본 HTTP 상태를 지정한다.
- `void` Controller: body 없이 상태 코드와 header만 반환할 수 있다.
- method chaining: Cookie Provider 반환 객체에 `toString()`을 이어 호출한다.

## 다음 파일

`SecurityConfig.java`에서 이 endpoint들이 인증 Filter와 어떤 순서·허용 규칙으로 연결되는지 읽는다.

현재 파일 진행률: **31개 확인 완료 / 최소 학습 대상 213개 = 약 14.6%**
