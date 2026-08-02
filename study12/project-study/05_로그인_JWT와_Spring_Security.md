# 5장. 로그인, JWT와 Spring Security

## 5.1 학습 목표

로그인부터 Access Token 검증, Refresh Token 회전, 로그아웃까지의 보안 흐름을 학습한다.

```text
로그인
→ 사용자 인증
→ Access Token 발급
→ Refresh Token 발급
→ Refresh Token 해시를 DB에 저장
→ 원문은 HttpOnly Cookie
→ Access Token은 프론트 localStorage
```

## 5.2 실제 코드 발췌: 보안 경로 설정

```java
@Bean
public SecurityFilterChain securityFilterChain(HttpSecurity http)
        throws Exception {
    http
            .cors(Customizer.withDefaults())
            .csrf(AbstractHttpConfigurer::disable)
            .sessionManagement(session -> session
                    .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
            )
            .authorizeHttpRequests(auth -> auth
                    .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                    .requestMatchers(HttpMethod.GET, "/actuator/health").permitAll()
                    .requestMatchers(HttpMethod.POST, "/users").permitAll()
                    .requestMatchers(
                            HttpMethod.POST,
                            "/sessions",
                            "/sessions/refresh"
                    ).permitAll()
                    .requestMatchers(HttpMethod.DELETE, "/sessions").permitAll()
                    .anyRequest().authenticated()
            )
            .addFilterBefore(
                    jwtAuthenticationFilter,
                    UsernamePasswordAuthenticationFilter.class
            );

    return http.build();
}
```

중요한 줄:

```java
SessionCreationPolicy.STATELESS
// 서버의 HTTP Session에 로그인 상태를 저장하지 않고 매 요청의 토큰을 검사한다.

.anyRequest().authenticated()
// 공개 경로로 지정하지 않은 나머지 요청은 인증이 필요하다.

.addFilterBefore(jwtAuthenticationFilter, ...)
// Controller 전에 JWT Filter를 실행한다.
```

CSRF를 껐지만 Refresh Cookie를 사용하는 세션 API는 `SessionOriginInterceptor`가 허용 Origin인지 별도로 검사한다.

## 5.3 실제 코드 발췌: Access Token 생성

```java
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
```

JWT에 들어가는 핵심 정보:

```text
subject
→ 사용자 ID

authVersion
→ 비밀번호 변경·탈퇴 등으로 이전 토큰을 무효화하기 위한 버전

issuedAt / expiration
→ 발급 시간과 만료 시간

signWith
→ 서버 비밀키로 서명
```

JWT는 암호화된 비밀 저장소가 아니다. 서명은 내용 변조 여부를 검증하며, 민감한 비밀번호를 claim에 넣으면 안 된다.

## 5.4 실제 코드 발췌: JWT Filter

```java
String authorizationHeader = request.getHeader("Authorization");

if (authorizationHeader == null
        || !authorizationHeader.startsWith("Bearer ")) {
    filterChain.doFilter(request, response);
    return;
}

try {
    String token = authorizationHeader.substring(7);
    AccessTokenClaims tokenClaims =
            jwtProvider.getAccessTokenClaims(token);

    CustomUserDetails userDetails =
            customUserDetailsService.loadUserByUserId(
                    tokenClaims.userId()
            );

    if (!userDetails.isEnabled()
            || userDetails.getAuthVersion()
                    != tokenClaims.authVersion()) {
        throw new DataNullException("No_User");
    }

    UsernamePasswordAuthenticationToken authentication =
            new UsernamePasswordAuthenticationToken(
                    userDetails,
                    null,
                    userDetails.getAuthorities()
            );

    SecurityContextHolder.getContext()
            .setAuthentication(authentication);

    filterChain.doFilter(request, response);
} catch (AuthException | DataNullException e) {
    SecurityContextHolder.clearContext();
    response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
    response.getWriter()
            .write("{\"message\":\"Invalid_Token\"}");
}
```

Filter는 다음 순서로 판단한다.

```text
Bearer 헤더 확인
→ JWT 서명과 만료 검증
→ 사용자 DB 조회
→ 삭제·정지 상태와 authVersion 확인
→ SecurityContext에 인증 저장
→ Controller 진행
```

헤더가 없으면 Filter가 바로 401을 만들지 않고 다음 Filter로 넘긴다. 이후 보호된 경로라면 Security 설정의 AuthenticationEntryPoint가 401을 반환한다.

## 5.5 실제 코드 발췌: Refresh Token과 쿠키

```java
public String createRefreshToken() {
    byte[] tokenBytes = new byte[TOKEN_BYTE_LENGTH];
    secureRandom.nextBytes(tokenBytes);

    return Base64.getUrlEncoder()
            .withoutPadding()
            .encodeToString(tokenBytes);
}

public String hashRefreshToken(String refreshToken) {
    MessageDigest messageDigest =
            MessageDigest.getInstance("SHA-256");

    byte[] tokenHash = messageDigest.digest(
            refreshToken.getBytes(StandardCharsets.UTF_8)
    );

    return HexFormat.of().formatHex(tokenHash);
}
```

Refresh Token은 JWT가 아니라 예측하기 어려운 무작위 문자열이다. DB에는 원문 대신 해시를 저장한다.

```java
return ResponseCookie.from(COOKIE_NAME, refreshToken)
        .httpOnly(true)
        .secure(secure)
        .sameSite("Strict")
        .path(cookiePath)
        .maxAge(refreshExpiration)
        .build();
```

| 속성 | 의미 |
|---|---|
| `HttpOnly` | JavaScript에서 쿠키 원문 접근 차단 |
| `Secure` | HTTPS에서만 전송 |
| `SameSite=Strict` | 다른 사이트에서 시작된 요청의 쿠키 전송 제한 |
| `Path` | 쿠키를 전송할 URL 범위 제한 |
| `Max-Age` | 쿠키 유효 시간 |

## 5.6 실제 코드 발췌: 프론트 요청과 자동 재발급

```javascript
async function sendRequest(
    endpoint,
    options = {},
    includeAccessToken = true,
) {
    const accessToken = authStorage.getAccessToken();
    const headers = {
        "Content-Type": "application/json",
        ...options.headers,
    };

    if (includeAccessToken && accessToken) {
        headers.Authorization = `Bearer ${accessToken}`;
    }

    const response = await fetch(`${API_BASE_URL}${endpoint}`, {
        ...options,
        headers,
        credentials: "include",
    });

    return {
        response,
        data: await readResponseData(response),
    };
}
```

중요한 줄:

```javascript
headers.Authorization = `Bearer ${accessToken}`;
// Access Token을 HTTP 헤더로 보낸다.

credentials: "include"
// 브라우저가 Refresh Token Cookie를 요청에 포함하고 응답 쿠키도 처리하게 한다.
```

자동 재발급 흐름:

```javascript
if (
    result.response.status === 401
    && !isRefreshExcludedRequest(endpoint, options)
) {
    await refreshAccessToken();
    result = await sendRequest(endpoint, options);
}
```

```text
원래 요청이 401
→ /sessions/refresh
→ 브라우저가 HttpOnly Refresh Cookie 전송
→ 서버가 DB의 해시와 세션 상태 확인
→ Refresh Token 회전
→ 새 Access Token 저장
→ 원래 요청 한 번 재시도
```

`refreshPromise`는 여러 요청이 동시에 401을 받았을 때 재발급 요청을 하나로 합친다.


### 401 자동 재발급 분기 라인별 주석본

```javascript
if ( // 자동 재발급을 시도할 조건 검사를 시작한다.
    result.response.status === 401 // 첫 요청이 인증 실패 상태인지 확인한다.
    && !isRefreshExcludedRequest(endpoint, options) // 로그인·재발급·로그아웃처럼 재발급하면 안 되는 요청은 제외한다.
) {
    await refreshAccessToken(); // HttpOnly Refresh Cookie로 새 Access Token을 발급받아 저장할 때까지 기다린다.
    result = await sendRequest(endpoint, options); // 새 Access Token이 포함된 동일한 원래 요청을 정확히 한 번 다시 보낸다.
}
```

재시도 결과도 401이면 상위 `request()`의 실패 처리에서 인증 정보를 제거하고 로그인 화면으로 이동한다.

## 5.7 비밀번호 변경과 `authVersion`

비밀번호 변경 시 다음 두 작업을 수행한다.

```text
User.authVersion 증가
→ 기존 Access Token claim과 DB 버전이 달라짐

활성 AuthSession 전부 revoke
→ 기존 Refresh Token 사용 불가
```

따라서 탈취된 이전 Access Token과 Refresh Token을 함께 무효화할 수 있다.

## 5.8 핵심 축약본

```text
Access Token
→ 짧은 수명
→ localStorage
→ Authorization 헤더
→ 매 요청 서명·만료·authVersion 검사

Refresh Token
→ 긴 수명
→ HttpOnly Cookie
→ DB에는 SHA-256 해시
→ Access Token 재발급 때 회전
```


## 5.8.1 전체 원문 코드 라인별 주석본

아래 주석은 학습을 위해 추가한 것이며 실제 프로젝트 파일에는 없다. 줄 끝 주석을 붙인 주석본은 의미를 따라가기 위한 설명용이므로 그대로 복사해 실행하지 않는다.

### `SecurityFilterChain`

```java
@Bean // 메서드 반환값을 Spring SecurityFilterChain Bean으로 등록한다.
public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception { // HTTP 보안 규칙 builder를 받아 Filter Chain을 만든다.
    http // 전달받은 보안 설정 builder에 규칙을 순서대로 추가한다.
            .cors(Customizer.withDefaults()) // WebConfig의 CORS 설정을 Security에도 적용한다.
            .csrf(AbstractHttpConfigurer::disable) // 기본 CSRF token 검사를 끈다.
            .sessionManagement(session -> session // 서버 HTTP Session 정책 설정을 시작한다.
                    .sessionCreationPolicy(SessionCreationPolicy.STATELESS) // 로그인 상태를 Session에 저장하지 않고 매 요청 토큰을 검사한다.
            )
            .authorizeHttpRequests(auth -> auth // URL과 HTTP method별 접근 권한 설정을 시작한다.
                    .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll() // 브라우저 CORS 사전 요청은 인증 없이 허용한다.
                    .requestMatchers(HttpMethod.GET, "/actuator/health").permitAll() // 배포 health check는 공개한다.
                    .requestMatchers(HttpMethod.POST, "/users").permitAll() // 로그인 전 회원가입을 허용한다.
                    .requestMatchers(HttpMethod.POST, "/sessions", "/sessions/refresh").permitAll() // 로그인과 토큰 재발급을 공개한다.
                    .requestMatchers(HttpMethod.DELETE, "/sessions").permitAll() // 토큰이 만료된 상태에서도 Refresh Cookie로 로그아웃할 수 있게 한다.
                    .anyRequest().authenticated() // 위에 없는 모든 요청은 인증을 요구한다.
            )
            .addFilterBefore( // 사용자 정의 Filter의 실행 위치를 지정한다.
                    jwtAuthenticationFilter, // 매 요청의 Bearer Access Token을 검사할 Filter다.
                    UsernamePasswordAuthenticationFilter.class // 기본 사용자·비밀번호 Filter보다 앞에 둔다.
            );

    return http.build(); // 누적한 설정으로 실제 SecurityFilterChain을 완성해 반환한다.
}
```

### Access Token 생성

```java
public String createAccessToken(Long userId, long authVersion) { // 사용자 ID와 현재 인증 버전으로 JWT를 만든다.
    Date now = new Date(); // 발급 시각으로 사용할 현재 시간을 만든다.
    Date expiration = new Date(now.getTime() + accessExpirationMillis); // 현재 시간에 설정된 수명을 더해 만료 시각을 만든다.

    return Jwts.builder() // JWT header와 payload를 만들 builder를 시작한다.
            .subject(String.valueOf(userId)) // subject claim에 사용자 ID 문자열을 넣는다.
            .claim(AUTH_VERSION_CLAIM, authVersion) // 사용자 보안 상태 버전을 별도 claim에 넣는다.
            .issuedAt(now) // 발급 시각 claim을 넣는다.
            .expiration(expiration) // 만료 시각 claim을 넣는다.
            .signWith(secretKey) // 서버의 SecretKey로 서명하여 변조 검증이 가능하게 한다.
            .compact(); // 모든 내용을 점으로 구분된 JWT 문자열로 직렬화한다.
}
```

### JWT Filter 핵심

```java
String authorizationHeader = request.getHeader("Authorization"); // 요청의 Authorization 헤더를 읽는다.

if (authorizationHeader == null || !authorizationHeader.startsWith("Bearer ")) { // 헤더가 없거나 Bearer 방식이 아닌지 확인한다.
    filterChain.doFilter(request, response); // 이 Filter에서는 인증을 만들지 않고 다음 Filter로 넘긴다.
    return; // 아래 JWT 파싱 코드는 실행하지 않는다.
}

try { // 토큰 파싱과 사용자 검증 중 예상한 인증 오류를 처리하기 위한 범위다.
    String token = authorizationHeader.substring(7); // "Bearer " 일곱 글자를 제거하고 실제 JWT만 꺼낸다.
    AccessTokenClaims tokenClaims = jwtProvider.getAccessTokenClaims(token); // 서명·만료를 검증하고 사용자 ID와 authVersion을 얻는다.

    CustomUserDetails userDetails = // DB 사용자 정보를 담을 변수를 선언한다.
            customUserDetailsService.loadUserByUserId(tokenClaims.userId()); // JWT의 사용자 ID로 현재 사용자 상태를 조회한다.

    if (!userDetails.isEnabled() || userDetails.getAuthVersion() != tokenClaims.authVersion()) { // 정지·삭제 상태이거나 보안 버전이 바뀌었는지 검사한다.
        throw new DataNullException("No_User"); // 현재 토큰으로 인증할 수 없음을 예외로 표현한다.
    }

    UsernamePasswordAuthenticationToken authentication = // Spring Security가 이해할 인증 객체를 만든다.
            new UsernamePasswordAuthenticationToken( // 이미 검증된 사용자이므로 인증된 형태의 생성자를 사용한다.
                    userDetails, // Controller의 principal이 될 사용자 정보다.
                    null, // JWT 인증에서는 비밀번호 credential을 다시 보관하지 않는다.
                    userDetails.getAuthorities() // 사용자가 가진 권한 목록을 전달한다.
            );

    SecurityContextHolder.getContext().setAuthentication(authentication); // 현재 요청 Thread의 SecurityContext에 인증을 저장한다.
    filterChain.doFilter(request, response); // 인증된 상태로 다음 Filter와 Controller 실행을 계속한다.
} catch (AuthException | DataNullException e) { // 잘못된 JWT나 현재 사용할 수 없는 사용자를 처리한다.
    SecurityContextHolder.clearContext(); // 중간에 남을 수 있는 인증 정보를 제거한다.
    response.setStatus(HttpServletResponse.SC_UNAUTHORIZED); // 응답 상태를 401로 지정한다.
    response.getWriter().write("{\"message\":\"Invalid_Token\"}"); // Controller까지 가지 않고 JSON 오류를 직접 작성한다.
}
```

### Refresh Token 생성과 해시

```java
public String createRefreshToken() { // 예측하기 어려운 Refresh Token 원문을 만든다.
    byte[] tokenBytes = new byte[TOKEN_BYTE_LENGTH]; // 32byte 무작위 값을 담을 배열을 만든다.
    secureRandom.nextBytes(tokenBytes); // 보안용 난수 생성기로 배열을 채운다.

    return Base64.getUrlEncoder() // URL과 Cookie에 안전한 Base64 encoder를 선택한다.
            .withoutPadding() // 값 끝의 = padding을 제거한다.
            .encodeToString(tokenBytes); // byte 배열을 문자열 token으로 변환한다.
}

public String hashRefreshToken(String refreshToken) { // DB 저장과 조회에 사용할 token hash를 만든다.
    MessageDigest messageDigest = MessageDigest.getInstance("SHA-256"); // SHA-256 hash 구현을 얻는다.
    byte[] tokenHash = messageDigest.digest(refreshToken.getBytes(StandardCharsets.UTF_8)); // token 문자열을 byte로 바꿔 단방향 hash한다.
    return HexFormat.of().formatHex(tokenHash); // hash byte를 DB에 저장하기 쉬운 16진수 문자열로 바꾼다.
}
```

### Refresh Cookie

```java
return ResponseCookie.from(COOKIE_NAME, refreshToken) // refreshToken 이름과 원문 값으로 응답 Cookie builder를 시작한다.
        .httpOnly(true) // JavaScript의 document.cookie로 원문을 읽지 못하게 한다.
        .secure(secure) // 운영에서는 HTTPS 요청에서만 Cookie를 전송한다.
        .sameSite("Strict") // 다른 사이트에서 시작된 요청에는 Cookie 전송을 강하게 제한한다.
        .path(cookiePath) // 세션 API 경로에서만 Cookie가 전송되게 범위를 제한한다.
        .maxAge(refreshExpiration) // 설정된 Refresh Token 수명만큼 브라우저에 보관한다.
        .build(); // 설정을 적용한 ResponseCookie 객체를 만든다.
```

### 프론트 공통 요청

```javascript
async function sendRequest(endpoint, options = {}, includeAccessToken = true) { // endpoint와 fetch 옵션을 받아 실제 HTTP 요청을 수행한다.
    const accessToken = authStorage.getAccessToken(); // localStorage에 저장된 Access Token을 읽는다.
    const headers = { // 모든 JSON API가 사용할 HTTP header 객체를 만든다.
        "Content-Type": "application/json", // 요청 body가 JSON임을 서버에 알린다.
        ...options.headers, // 호출자가 추가한 header가 있으면 합친다.
    };

    if (includeAccessToken && accessToken) { // 토큰 포함 요청이며 실제 저장된 토큰도 있는지 확인한다.
        headers.Authorization = `Bearer ${accessToken}`; // Bearer 방식 Authorization 헤더를 추가한다.
    }

    const response = await fetch(`${API_BASE_URL}${endpoint}`, { // 기준 주소와 endpoint를 합쳐 네트워크 요청을 보낸다.
        ...options, // method와 body 같은 호출자 옵션을 적용한다.
        headers, // 위에서 완성한 header를 적용한다.
        credentials: "include", // 해당 origin의 Cookie를 요청과 응답에서 처리하게 한다.
    });

    return { // HTTP 상태와 파싱된 body를 함께 반환한다.
        response, // status와 ok를 검사할 원본 Response다.
        data: await readResponseData(response), // 응답 body를 JSON 또는 문자열로 한 번 읽어 반환한다.
    };
}
```

## 5.9 스킵할 코드

- `AccessTokenClaims`는 JWT에서 꺼낸 사용자 ID와 버전을 담는 record다.
- `CustomUserDetails`의 Spring Security 인터페이스 메서드는 대표 메서드만 이해한다.
- Session DTO는 토큰과 사용자 ID를 전달하는 데이터 모양만 확인한다.
- `AuthSessionCleanupScheduler`는 만료된 Refresh Session 행을 주기적으로 삭제한다는 맥락만 먼저 확인한다.


## 5.9.1 이 장에서 필요한 Security·JavaScript 문법

### `@Bean`과 의존성 주입

`@Bean` 메서드의 반환 객체를 Spring Container가 관리한다. 다른 Bean 생성자가 그 타입을 요구하면 Spring이 같은 객체를 주입한다.

### Java lambda

```java
session -> session.sessionCreationPolicy(...)
```

화살표 왼쪽은 전달받는 인자, 오른쪽은 실행할 코드다. 다음 익명 함수와 같은 목적이다.

```text
session 인자를 받아
→ sessionCreationPolicy 메서드 호출
```

Security DSL은 lambda를 이용해 각 설정 영역만 열어 준다.

### Method reference

```java
AbstractHttpConfigurer::disable
```

`::`는 이미 존재하는 메서드를 lambda처럼 전달하는 method reference다. 개념적으로 `configurer -> configurer.disable()`과 같다.

### Filter Chain

Filter는 연결된 순서대로 요청을 받고 `filterChain.doFilter`를 호출해야 다음 Filter로 진행한다.

```text
Filter A 전처리
→ Filter B 전처리
→ Controller
→ Filter B 후처리
→ Filter A 후처리
```

Filter가 응답을 직접 작성하고 `doFilter`를 호출하지 않으면 그 자리에서 요청 흐름이 끝난다.

### `try`와 여러 예외 `catch`

```java
try {
    위험한 인증 처리
} catch (AuthException | DataNullException e) {
    실패 응답
}
```

세로줄 `|`은 이 catch가 둘 중 어느 예외든 처리한다는 multi-catch 문법이다.

### `substring(7)`

문자열 index 7부터 끝까지 새 문자열을 만든다.

```text
"Bearer abc"
 0123456
→ substring(7)
→ "abc"
```

### `record`

```java
public record AccessTokenClaims(
    Long userId,
    long authVersion
) {}
```

record는 변경 불가능한 데이터 묶음에 적합하며 생성자, accessor, `equals`, `hashCode`, `toString`을 자동 생성한다. getter 이름은 `getUserId()`가 아니라 `userId()`다.

### `async`와 `await`

```javascript
async function sendRequest() {
    const response = await fetch(...);
}
```

- `async` 함수는 항상 Promise를 반환한다.
- `await`는 Promise가 완료될 때까지 해당 async 함수의 다음 줄 실행을 보류한다.
- 브라우저 Thread 전체를 멈추는 동기식 대기는 아니다.

### Promise와 `finally`

```javascript
refreshPromise = performRefresh().finally(() => {
    refreshPromise = null;
});
```

`finally` callback은 Promise 성공·실패 모두에서 실행된다. 완료 뒤 공유 변수를 null로 돌려 다음 재발급 요청이 새 Promise를 만들게 한다.

### Template literal

```javascript
`Bearer ${accessToken}`
`${API_BASE_URL}${endpoint}`
```

백틱 문자열 안의 `${...}`에 JavaScript 값을 삽입한다. YAML placeholder나 GitHub 표현식과 모양은 비슷하지만 평가 주체가 JavaScript다.

### Spread 문법

```javascript
const headers = {
    "Content-Type": "application/json",
    ...options.headers,
};
```

`...객체`는 그 객체의 속성을 현재 객체에 펼쳐 복사한다. 뒤에 위치한 같은 이름 속성이 앞 값을 덮어쓴다.

```javascript
{
    ...options,
    headers,
}
```

호출자의 fetch 옵션을 먼저 복사하고, 마지막 `headers`는 공통 코드가 완성한 값으로 덮어쓴다.

### `Set`

```javascript
new Set(["POST /sessions", ...])
```

중복 없는 값 모음이다. `has(value)`로 특정 method와 endpoint 조합이 재발급 제외 목록에 있는지 빠르게 확인한다.

### localStorage와 Cookie

- localStorage는 JavaScript가 직접 읽고 쓰며 브라우저를 닫았다 열어도 남을 수 있다.
- HttpOnly Cookie는 JavaScript가 원문을 읽을 수 없고 브라우저가 조건에 맞는 HTTP 요청에 자동 첨부한다.

## 5.10 이해 확인

1. Access Token과 Refresh Token은 형식, 저장 위치, 수명이 어떻게 다른가?
2. DB에 Refresh Token 원문 대신 해시를 저장하는 이유는 무엇인가?
3. `STATELESS`는 무엇을 서버 Session에 저장하지 않는다는 뜻인가?
4. JWT Filter는 어떤 순서로 Access Token을 검사하는가?
5. `authVersion`이 기존 Access Token을 무효화하는 원리는 무엇인가?
6. `HttpOnly`, `Secure`, `SameSite`는 각각 무엇을 제한하는가?
7. API 요청이 401을 받았을 때 프론트는 어떤 순서로 처리하는가?
8. `refreshPromise`가 필요한 이유는 무엇인가?
9. CSRF를 비활성화했는데 세션 API에서 Origin을 검사하는 이유는 무엇인가?

## 5.11 오답노트

이 장의 이해 확인에서 틀리거나 핵심이 부족한 문제를 여기에 누적한다.
