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

### 5.1.1 이 장의 실제 코드 읽기 순서

```text
로그인 화면
→ authApi.login()
→ request(POST /sessions)
→ SessionOriginInterceptor
→ SecurityFilterChain의 permitAll
→ SessionController
→ SessionService
→ AuthenticationManager
→ CustomUserDetailsService → UserRepository
→ PasswordEncoder 검증
→ JwtProvider + RefreshTokenProvider
→ AuthSessionRepository 저장
→ Access Token response body + Refresh Token Set-Cookie
```

로그인 뒤 보호 API는 `request()`가 `Authorization` header를 붙이고, `JwtAuthenticationFilter`가 token claim과 현재 사용자의 `authVersion`을 대조한 뒤 `SecurityContext`에 인증 정보를 넣는다. Access Token 만료로 401이 오면 프론트의 공유 `refreshPromise`가 `/sessions/refresh` 한 번으로 요청들을 모으고, 백엔드는 `AuthSession` row lock 안에서 Refresh Token을 회전한다. 이 장은 이 세 흐름을 `로그인 → 일반 인증 요청 → 재발급` 순서로 읽는다.

## 5.2 실제 코드 발췌: 보안 경로 설정

```java
@Bean
public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
    http
            .cors(Customizer.withDefaults())

            .csrf(AbstractHttpConfigurer::disable)

            .sessionManagement(session -> session
                    .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
            )

            .exceptionHandling(exception -> exception
                    .authenticationEntryPoint((request, response, authException) -> {
                        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                        response.setContentType("application/json;charset=UTF-8");
                        response.getWriter().write("{\"message\":\"Unauthorized\"}");
                    })
                    .accessDeniedHandler((request, response, accessDeniedException) -> {
                        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                        response.setContentType("application/json;charset=UTF-8");
                        response.getWriter().write("{\"message\":\"Forbidden\"}");
                    })
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

`exceptionHandling`은 Controller에 도달하지 못한 Spring Security 실패를 직접 응답으로 바꾼다.

```text
인증이 필요한 경로인데 인증 객체가 없음
→ authenticationEntryPoint
→ 401 { "message": "Unauthorized" }

인증 객체는 있지만 접근 권한이 부족함
→ accessDeniedHandler
→ 403 { "message": "Forbidden" }
```

이 응답들은 `GlobalExceptionHandler`가 만드는 것이 아니다. Security Filter Chain 안에서 status, content type, body를 직접 작성한다.

CSRF를 껐지만 세션 API는 `SessionOriginInterceptor`가 `Origin`을 별도로 검사한다. 실제 연결과 판단 코드는 다음과 같다.

```java
@Override
public void addInterceptors(InterceptorRegistry registry) {
    registry.addInterceptor(requestLogInterceptor)
            .addPathPatterns("/**");

    registry.addInterceptor(sessionOriginInterceptor)
            .addPathPatterns("/sessions", "/sessions/refresh");
}
```

```java
@Override
public boolean preHandle(
        HttpServletRequest request,
        HttpServletResponse response,
        Object handler
) throws IOException {
    if (!isCookieSessionRequest(request)) {
        return true;
    }

    String origin = request.getHeader("Origin");

    if (corsOriginProvider.isAllowed(origin)) {
        return true;
    }

    response.setStatus(HttpServletResponse.SC_FORBIDDEN);
    response.setContentType("application/json;charset=UTF-8");
    response.getWriter().write("{\"message\":\"Forbidden_Origin\"}");
    return false;
}

private boolean isCookieSessionRequest(HttpServletRequest request) {
    String method = request.getMethod();
    String path = request.getRequestURI();

    return method.equals("POST") && path.equals("/sessions")
            || method.equals("POST") && path.equals("/sessions/refresh")
            || method.equals("DELETE") && path.equals("/sessions");
}
```

실제 Origin 허용 판단 코드:

```java
public boolean isAllowed(String origin) {
    return origin != null && allowedOrigins.contains(origin);
}
```

`preHandle()`이 `false`를 반환하면 Controller를 실행하지 않는다. 현재 정책은 설정된 허용 목록과 정확히 일치하는 Origin만 통과시킨다.

```text
허용 목록에 있는 Origin
→ Controller 실행

허용 목록에 없는 Origin
→ 403 Forbidden_Origin

Origin header 없음
→ 403 Forbidden_Origin
```

현재 React frontend의 로그인·재발급·로그아웃은 browser `fetch()`의 POST·DELETE 요청이므로 Origin을 전달한다. 반면 Origin을 넣지 않은 MockMvc, Postman과 curl 요청은 거부되며, 수동 호출이 필요하면 `cors.allowed-origins`에 등록된 Origin header를 명시해야 한다. Refresh Cookie의 `SameSite=Strict`와 이 검사는 서로 다른 방어층이다.

실제 보안 테스트의 관련 원문:

```java
private static final String ALLOWED_ORIGIN = "http://localhost:5173";

@Test
void 로그인_API는_인증_없이_접근할_수_있다() throws Exception {
    mockMvc.perform(
                    post("/sessions")
                            .header(HttpHeaders.ORIGIN, ALLOWED_ORIGIN)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}")
            )
            .andExpect(status().isBadRequest());
}

@Test
void Origin이_없는_세션_API_요청은_거부한다() throws Exception {
    mockMvc.perform(
                    post("/sessions")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}")
            )
            .andExpect(status().isForbidden())
            .andExpect(content().contentType("application/json;charset=UTF-8"))
            .andExpect(jsonPath("$.message").value("Forbidden_Origin"));
}

@Test
void 허용되지_않은_Origin의_세션_API_요청은_거부한다() throws Exception {
    mockMvc.perform(
                    post("/sessions")
                            .header(HttpHeaders.ORIGIN, "http://attacker.example")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}")
            )
            .andExpect(status().isForbidden());
}
```

첫 테스트가 `400`을 기대하는 이유는 허용 Origin 검사를 통과한 뒤 빈 로그인 DTO의 입력값 검증에서 실패하기 때문이다. Origin 누락 요청은 `SessionOriginInterceptor`가 JSON 본문과 함께 403으로 거부한다. 허용되지 않은 Origin은 Spring의 CORS 처리가 interceptor보다 먼저 403으로 거부할 수 있으므로 마지막 테스트는 status만 검증한다.

## 5.2.1 실제 로그인 처리 흐름

파일: `service/SessionService.java`

```java
public SessionResponseDto createSession(SessionRequestDto request) {
    try {
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        request.getEmail(),
                        request.getPassword()
                )
        );

        CustomUserDetails userDetails = (CustomUserDetails) authentication.getPrincipal();

        String accessToken = jwtProvider.createAccessToken(
                userDetails.getUserId(),
                userDetails.getAuthVersion()
        );
        String refreshToken = refreshTokenProvider.createRefreshToken();
        String refreshTokenHash = refreshTokenProvider.hashRefreshToken(refreshToken);

        AuthSession authSession = new AuthSession(
                userDetails.getUser(),
                refreshTokenHash,
                refreshTokenProvider.createExpirationTime()
        );
        authSessionRepository.save(authSession);

        return new SessionResponseDto(
                accessToken,
                refreshToken,
                userDetails.getUserId()
        );

    } catch (DisabledException e) {
        throw new LoginFailedException("Suspended_Account");
    } catch (AuthenticationException e) {
        throw new LoginFailedException("Login_Failed");
    }
}
```

`AuthenticationManager`는 email과 평문 password를 직접 DB 문자열과 비교하는 코드가 아니다. Spring Security가 `CustomUserDetailsService.loadUserByUsername(email)`로 사용자를 불러오고, 등록된 `BCryptPasswordEncoder`를 이용해 입력 password와 저장된 BCrypt hash가 맞는지 검사한다. 인증에 성공한 `Authentication`의 principal에는 `CustomUserDetails`가 들어간다.

Controller는 Refresh Token 원문을 JSON에 넣지 않고 `Set-Cookie` header로 전달한다.

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

`SessionResponseDto.refreshToken`에는 `@JsonIgnore`가 붙어 있다. Service와 Controller 사이는 원문을 전달하지만 Jackson이 JSON response body를 만들 때는 제외한다. 최종 응답은 새 Refresh Cookie와 Access Token JSON을 서로 다른 위치에 전달한다.

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

Access Token을 검증하고 claim을 꺼내는 실제 원문:

```java
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
```

`verifyWith(secretKey)`는 같은 secret key로 signature가 유효한지 확인한다. `parseSignedClaims()` 과정에서는 형식·signature·만료 등의 JWT 검증 실패가 `JwtException` 계열로 발생할 수 있다. subject를 `Long`으로 바꿀 수 없거나 다른 argument가 잘못된 경우까지 `IllegalArgumentException`으로 묶어 프로젝트의 `Invalid_Token`으로 통일한다.

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

공개 요청에서는 Filter 자체를 건너뛰는 실제 원문:

```java
@Override
protected boolean shouldNotFilter(HttpServletRequest request) {
    String method = request.getMethod();
    String path = request.getServletPath();

    return method.equals("OPTIONS")
            || method.equals("POST") && path.equals("/users")
            || method.equals("POST") && path.equals("/sessions")
            || method.equals("POST") && path.equals("/sessions/refresh")
            || method.equals("DELETE") && path.equals("/sessions");
}
```

`OncePerRequestFilter`는 한 요청 dispatch에서 Filter를 한 번 실행하는 기반 class다. `shouldNotFilter()`가 `true`를 반환하면 `doFilterInternal()`을 호출하지 않는다. `&&`가 `||`보다 먼저 계산되므로 각 줄은 “해당 method이면서 해당 path”라는 한 조건이다.

토큰을 검사하고 인증을 저장하는 `doFilterInternal()`의 실제 원문:

```java
@Override
protected void doFilterInternal(
        HttpServletRequest request,
        HttpServletResponse response,
        FilterChain filterChain
) throws ServletException, IOException {

    String authorizationHeader = request.getHeader("Authorization");

    if (authorizationHeader == null || !authorizationHeader.startsWith("Bearer ")) {
        filterChain.doFilter(request, response);
        return;
    }

    try {
        String token = authorizationHeader.substring(7);
        AccessTokenClaims tokenClaims = jwtProvider.getAccessTokenClaims(token);

        CustomUserDetails userDetails =
                customUserDetailsService.loadUserByUserId(tokenClaims.userId());

        if (!userDetails.isEnabled()
                || userDetails.getAuthVersion() != tokenClaims.authVersion()) {
            throw new DataNullException("No_User");
        }

        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(
                        userDetails,
                        null,
                        userDetails.getAuthorities()
                );
        authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

        SecurityContextHolder.getContext().setAuthentication(authentication);

        filterChain.doFilter(request, response);

    } catch (AuthException | DataNullException e) {
        SecurityContextHolder.clearContext();

        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write("{\"message\":\"Invalid_Token\"}");
    }
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

`authentication.setDetails(...)`는 IP 주소와 session ID 같은 요청 세부 정보를 authentication 객체에 붙인다. 이 프로젝트의 Controller 인증 판단에 직접 사용되지는 않지만 Spring Security의 표준 authentication 모양을 완성한다.

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
```

Refresh Token은 JWT가 아니라 예측하기 어려운 무작위 문자열이다. DB에는 원문 대신 해시를 저장한다.

`MessageDigest.getInstance()`는 요청한 hash algorithm을 JVM이 제공하지 않으면 checked exception인 `NoSuchAlgorithmException`을 던질 수 있다. 현재 코드는 이를 잡아 실행 환경 설정이 잘못된 상태를 뜻하는 `IllegalStateException`으로 바꾼다. 일반적인 JVM에는 SHA-256이 제공되므로 사용자 입력 오류로 처리하지 않는다.

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

### Refresh Session 회전의 실제 코드

Repository 조회에는 실제로 비관적 write lock이 적용되어 있다.

파일: `repository/AuthSessionRepository.java`

```java
@Lock(LockModeType.PESSIMISTIC_WRITE)
Optional<AuthSession> findByRefreshTokenHash(String refreshTokenHash);
```

파일: `service/SessionService.java`

```java
public SessionRefreshResponseDto refreshSession(String refreshToken) {
    if (refreshToken == null || refreshToken.isBlank()) {
        throw new AuthException("Invalid_Refresh_Token");
    }

    String refreshTokenHash = refreshTokenProvider.hashRefreshToken(refreshToken);
    AuthSession authSession = authSessionRepository
            .findByRefreshTokenHash(refreshTokenHash)
            .orElseThrow(() -> new AuthException("Invalid_Refresh_Token"));

    if (!authSession.isActive(LocalDateTime.now())) {
        throw new AuthException("Invalid_Refresh_Token");
    }

    if (authSession.getUser().isDeleted() || authSession.getUser().isSuspended()) {
        throw new AuthException("Invalid_Refresh_Token");
    }

    String newRefreshToken = refreshTokenProvider.createRefreshToken();
    String newRefreshTokenHash = refreshTokenProvider.hashRefreshToken(
            newRefreshToken
    );
    authSession.rotate(
            newRefreshTokenHash,
            refreshTokenProvider.createExpirationTime()
    );

    String accessToken = jwtProvider.createAccessToken(
            authSession.getUser().getUserId(),
            authSession.getUser().getAuthVersion()
    );

    return new SessionRefreshResponseDto(
            accessToken,
            newRefreshToken
    );
}
```

회전은 기존 DB row의 Refresh Token hash와 만료 시간을 새 값으로 교체하는 것이다. `SessionService` class 전체에는 `@Transactional`이 적용되어 있으므로 Repository가 얻은 write lock은 회전된 hash가 commit될 때까지 유지된다.

같은 기존 Refresh Token으로 요청 A와 B가 동시에 들어오면 다음 순서가 된다.

```text
요청 A가 기존 hash의 AuthSession row에 write lock 획득
→ 요청 B는 같은 row 조회에서 대기
→ 요청 A가 hash를 새 값으로 회전하고 commit
→ 요청 A의 lock 해제
→ 요청 B의 old hash 조회 결과 없음
→ Invalid_Refresh_Token
```

따라서 현재 코드에서는 같은 기존 Refresh Token으로 들어온 동시 재발급 요청 중 정상적으로 하나만 성공한다. `refreshPromise`는 한 browser 안에서 불필요한 중복 요청을 줄이는 frontend 장치이고, DB write lock은 여러 browser·server 요청까지 포함해 backend에서 실제 회전 경쟁을 직렬화하는 장치다.

## 5.6 실제 코드 발췌: 프론트 요청과 자동 재발급

```javascript
async function sendRequest(endpoint, options = {}, includeAccessToken = true) {
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
async function performRefresh() {
    const result = await sendRequest(
        "/sessions/refresh",
        { method: "POST" },
        false,
    );

    if (!result.response.ok) {
        throw createRequestError(result.response, result.data);
    }

    if (!result.data?.accessToken) {
        throw new Error("액세스 토큰 재발급 응답이 올바르지 않습니다.");
    }

    authStorage.setAccessToken(result.data.accessToken);
    return result.data;
}

export function refreshAccessToken() {
    if (!refreshPromise) {
        refreshPromise = performRefresh().finally(() => {
            refreshPromise = null;
        });
    }

    return refreshPromise;
}
```

위 블록은 실제 원문이다. `sendRequest(..., false)`의 세 번째 argument가 `false`이므로 만료된 Access Token을 refresh 요청의 Authorization header에 다시 넣지 않는다. Refresh Cookie는 `credentials: "include"`에 의해 별도로 전송된다.

401을 확인하고 원래 요청을 재시도하는 부분은 4장에서 `request()` 전체 원문으로 이미 확인했다. 여기서는 보안 흐름과 직접 관련된 실제 분기만 다시 본다.

```javascript
if (
    result.response.status === 401 &&
    !isRefreshExcludedRequest(endpoint, options)
) {
    try {
        await refreshAccessToken();
        result = await sendRequest(endpoint, options);
    } catch (error) {
        if (error.status === 401) {
            clearAuthentication();
        }

        throw error;
    }
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
    result.response.status === 401 && // 첫 요청이 인증 실패 상태인지 확인하고 다음 조건과 AND로 연결한다.
    !isRefreshExcludedRequest(endpoint, options) // 로그인·재발급·로그아웃·회원가입처럼 재발급하면 안 되는 요청은 제외한다.
) {
    try { // token 재발급과 원래 요청 재시도의 실패를 처리할 범위를 시작한다.
        await refreshAccessToken(); // HttpOnly Refresh Cookie로 새 Access Token을 발급받아 저장할 때까지 기다린다.
        result = await sendRequest(endpoint, options); // 새 Access Token이 포함된 동일한 원래 요청을 정확히 한 번 다시 보낸다.
    } catch (error) { // refresh 요청 또는 내부 parsing 등이 실패하면 Error를 받는다.
        if (error.status === 401) { // refresh 요청의 최종 상태가 인증 실패인지 확인한다.
            clearAuthentication(); // 저장된 Access Token을 제거하고 공개 경로가 아니면 로그인 화면으로 이동한다.
        }

        throw error; // 원래 API 호출자도 실패를 처리할 수 있도록 같은 Error를 다시 던진다.
    }
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
            .exceptionHandling(exception -> exception // Spring Security 인증·인가 실패 응답 설정을 시작한다.
                    .authenticationEntryPoint((request, response, authException) -> { // 인증 객체가 없어 보호 경로에 들어갈 수 없을 때 실행한다.
                        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED); // HTTP 상태를 401로 지정한다.
                        response.setContentType("application/json;charset=UTF-8"); // body가 UTF-8 JSON임을 명시한다.
                        response.getWriter().write("{\"message\":\"Unauthorized\"}"); // Filter Chain에서 JSON body를 직접 작성한다.
                    })
                    .accessDeniedHandler((request, response, accessDeniedException) -> { // 인증됐지만 필요한 권한이 없을 때 실행한다.
                        response.setStatus(HttpServletResponse.SC_FORBIDDEN); // HTTP 상태를 403으로 지정한다.
                        response.setContentType("application/json;charset=UTF-8"); // body의 형식과 문자 encoding을 지정한다.
                        response.getWriter().write("{\"message\":\"Forbidden\"}"); // Controller 없이 JSON body를 직접 작성한다.
                    })
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

### 로그인 Service와 Controller

```java
public SessionResponseDto createSession(SessionRequestDto request) { // 검증된 로그인 DTO로 token과 Refresh Session을 만든다.
    try { // Spring Security 인증 실패를 프로젝트 예외로 바꿀 범위를 시작한다.
        Authentication authentication = authenticationManager.authenticate( // 등록된 사용자 조회와 password encoder를 사용해 인증한다.
                new UsernamePasswordAuthenticationToken( // 아직 인증되지 않은 email·password credential 객체를 만든다.
                        request.getEmail(), // username 역할을 하는 입력 email이다.
                        request.getPassword() // BCrypt hash와 비교할 평문 입력 password다.
                )
        );

        CustomUserDetails userDetails = (CustomUserDetails) authentication.getPrincipal(); // 성공한 인증의 principal을 프로젝트 사용자 type으로 변환한다.

        String accessToken = jwtProvider.createAccessToken( // 짧게 사용할 서명 JWT를 만든다.
                userDetails.getUserId(), // subject에 넣을 사용자 ID다.
                userDetails.getAuthVersion() // 이전 token 무효화에 쓸 현재 인증 version이다.
        );
        String refreshToken = refreshTokenProvider.createRefreshToken(); // browser Cookie에 전달할 무작위 원문을 만든다.
        String refreshTokenHash = refreshTokenProvider.hashRefreshToken(refreshToken); // DB에는 원문 대신 hash를 저장한다.

        AuthSession authSession = new AuthSession( // 장기 재발급 상태를 저장할 entity를 만든다.
                userDetails.getUser(), // session 소유 User entity다.
                refreshTokenHash, // 조회·검증용 Refresh Token hash다.
                refreshTokenProvider.createExpirationTime() // session 만료 시각이다.
        );
        authSessionRepository.save(authSession); // Refresh Session row를 DB에 저장한다.

        return new SessionResponseDto( // Controller로 반환할 로그인 결과 DTO를 만든다.
                accessToken, // JSON body에 포함할 Access Token이다.
                refreshToken, // @JsonIgnore되며 Controller의 Set-Cookie 생성에만 사용될 원문이다.
                userDetails.getUserId() // 로그인 사용자 식별자다.
        );

    } catch (DisabledException e) { // UserDetails.isEnabled()가 false인 사용자 인증 실패를 잡는다.
        throw new LoginFailedException("Suspended_Account"); // 정지 계정용 401 예외로 바꾼다.
    } catch (AuthenticationException e) { // 그 밖의 email·password 인증 실패를 잡는다.
        throw new LoginFailedException("Login_Failed"); // 일반 로그인 실패용 401 예외로 바꾼다.
    }
}
```

```java
@PostMapping // POST /sessions에 연결한다.
@ResponseStatus(HttpStatus.CREATED) // 정상 응답 상태를 201 Created로 지정한다.
public SessionResponseDto createSession( // 로그인 JSON body와 Cookie header를 함께 만드는 Controller method다.
        @Valid @RequestBody SessionRequestDto request, // 요청 JSON을 DTO로 바꾸고 validation annotation을 검사한다.
        HttpServletResponse response // Set-Cookie header를 직접 추가할 servlet 응답 객체다.
){
    SessionResponseDto sessionResponse = sessionService.createSession(request); // 실제 인증과 token 생성을 Service에 위임한다.

    response.addHeader( // HTTP response에 header 하나를 추가한다.
            HttpHeaders.SET_COOKIE, // 표준 Set-Cookie header 이름을 사용한다.
            refreshCookieProvider // Cookie 보안 속성을 조립하는 component다.
                    .createRefreshTokenCookie(sessionResponse.getRefreshToken()) // DTO 내부 원문으로 ResponseCookie를 만든다.
                    .toString() // header에 넣을 Set-Cookie 문자열로 바꾼다.
    );

    return sessionResponse; // @JsonIgnore된 refreshToken을 제외한 DTO가 JSON body로 직렬화된다.
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

```java
public AccessTokenClaims getAccessTokenClaims(String token) { // JWT를 검증하고 필요한 두 claim을 project record로 반환한다.
    try { // JWT library와 값 변환에서 발생할 오류를 인증 예외로 통일할 범위다.
        Claims claims = Jwts.parser() // JWT parser builder를 시작한다.
                .verifyWith(secretKey) // signature 검증에 사용할 server secret key를 설정한다.
                .build() // 실제 parser를 만든다.
                .parseSignedClaims(token) // 서명된 JWT의 형식·signature·만료를 검사해 parsing한다.
                .getPayload(); // 검증된 payload claim 모음을 꺼낸다.

        Object authVersionClaim = claims.get(AUTH_VERSION_CLAIM); // custom authVersion claim을 아직 확정되지 않은 Object로 읽는다.
        if (!(authVersionClaim instanceof Number authVersion)) { // 숫자 type인지 검사하면서 맞으면 authVersion 변수에 binding한다.
            throw new AuthException("Invalid_Token"); // 숫자가 아니거나 없으면 유효하지 않은 token으로 처리한다.
        }

        return new AccessTokenClaims( // Filter에 전달할 immutable record를 만든다.
                Long.valueOf(claims.getSubject()), // subject 문자열을 사용자 ID Long으로 바꾼다.
                authVersion.longValue() // Number를 primitive long 인증 version으로 바꾼다.
        );
    } catch (JwtException | IllegalArgumentException e) { // JWT 검증 오류와 숫자 변환·argument 오류를 함께 잡는다.
        throw new AuthException("Invalid_Token"); // 외부에는 library 세부 오류 대신 한 프로젝트 인증 오류를 전달한다.
    }
}
```

### `SessionOriginInterceptor`

Origin 허용 판단:

```java
public boolean isAllowed(String origin) { // 요청 Origin을 이 서비스가 신뢰하는지 반환한다.
    return origin != null && allowedOrigins.contains(origin); // header가 존재하고 설정의 허용 목록에 정확히 포함된 경우에만 true다.
}
```

`&&`는 왼쪽부터 평가하는 논리 AND다. `origin == null`이면 왼쪽이 false이므로 `contains()`를 호출하지 않고 전체 결과가 false가 된다. 따라서 null pointer 오류 없이 Origin 누락 요청을 거부한다.

`InterceptorConfig`의 연결 부분:

```java
@Override // WebMvcConfigurer의 interceptor 등록 method를 재정의한다.
public void addInterceptors(InterceptorRegistry registry) { // Spring MVC가 실행할 interceptor들을 registry에 추가한다.
    registry.addInterceptor(requestLogInterceptor) // 요청 log interceptor를 등록한다.
            .addPathPatterns("/**"); // 모든 MVC path에서 실행하게 한다.

    registry.addInterceptor(sessionOriginInterceptor) // 세션 Origin 검사 interceptor를 등록한다.
            .addPathPatterns("/sessions", "/sessions/refresh"); // 로그인·로그아웃 path와 refresh path에만 연결한다.
}
```

검사 부분:

```java
@Override // HandlerInterceptor의 Controller 전처리 method를 재정의한다.
public boolean preHandle( // Controller method 실행을 계속할지 boolean으로 반환한다.
        HttpServletRequest request, // 현재 HTTP 요청이다.
        HttpServletResponse response, // 거부할 때 직접 작성할 HTTP 응답이다.
        Object handler // 뒤에서 실행될 handler 정보이며 현재 method에서는 사용하지 않는다.
) throws IOException { // response writer 사용 중 발생 가능한 입출력 예외를 선언한다.
    if (!isCookieSessionRequest(request)) { // Origin 검사가 필요한 method와 path 조합이 아닌지 확인한다.
        return true; // true이면 다음 interceptor와 Controller 실행을 계속한다.
    }

    String origin = request.getHeader("Origin"); // browser가 보낸 요청 출처 header를 읽는다.

    if (corsOriginProvider.isAllowed(origin)) { // Origin이 존재하면서 설정된 허용 목록에 포함되는지 검사한다.
        return true; // 허용되면 Controller 실행을 계속한다.
    }

    response.setStatus(HttpServletResponse.SC_FORBIDDEN); // 허용되지 않은 Origin이면 403을 지정한다.
    response.setContentType("application/json;charset=UTF-8"); // 직접 작성할 body가 UTF-8 JSON임을 지정한다.
    response.getWriter().write("{\"message\":\"Forbidden_Origin\"}"); // 거부 이유를 JSON body로 쓴다.
    return false; // Controller 호출을 중단한다.
}

private boolean isCookieSessionRequest(HttpServletRequest request) { // Origin 검사가 필요한 세션 요청인지 계산한다.
    String method = request.getMethod(); // HTTP method를 읽는다.
    String path = request.getRequestURI(); // MockMvc와 실제 server에서 공통으로 요청 URI인 /sessions 또는 /sessions/refresh를 읽는다.

    return method.equals("POST") && path.equals("/sessions") // 로그인 POST인지 검사한다.
            || method.equals("POST") && path.equals("/sessions/refresh") // 재발급 POST인지 검사한다.
            || method.equals("DELETE") && path.equals("/sessions"); // 로그아웃 DELETE인지 검사한다.
}
```

Origin 정책 테스트:

```java
private static final String ALLOWED_ORIGIN = "http://localhost:5173"; // local·test 설정에 등록된 정상 frontend Origin을 재사용한다.

@Test // 아래 method를 JUnit test case로 등록한다.
void 로그인_API는_인증_없이_접근할_수_있다() throws Exception { // 정상 Origin은 보안 단계에서 막히지 않는지 확인한다.
    mockMvc.perform( // 가상 HTTP 요청을 Spring MVC에 보낸다.
                    post("/sessions") // 로그인 endpoint에 POST 요청을 만든다.
                            .header(HttpHeaders.ORIGIN, ALLOWED_ORIGIN) // 실제 browser 요청처럼 허용된 Origin header를 넣는다.
                            .contentType(MediaType.APPLICATION_JSON) // request body 형식을 JSON으로 지정한다.
                            .content("{}") // 입력값 검증 실패를 확인하기 위해 빈 JSON object를 보낸다.
            )
            .andExpect(status().isBadRequest()); // Origin 검사는 통과하고 DTO 검증에서 400이 되는지 확인한다.
}

@Test // Origin 누락 거부를 별도 case로 검증한다.
void Origin이_없는_세션_API_요청은_거부한다() throws Exception { // header가 없는 요청의 기대 동작을 test 이름에 표현한다.
    mockMvc.perform(
                    post("/sessions") // Origin 검사가 적용되는 로그인 POST를 만든다.
                            .contentType(MediaType.APPLICATION_JSON) // JSON body임을 지정한다.
                            .content("{}") // Controller 검증보다 Origin 검사가 먼저 실행되는지 확인할 body다.
            )
            .andExpect(status().isForbidden()) // interceptor가 HTTP 403을 반환하는지 확인한다.
            .andExpect(content().contentType("application/json;charset=UTF-8")) // 직접 작성한 응답의 content type을 확인한다.
            .andExpect(jsonPath("$.message").value("Forbidden_Origin")); // JSON 오류 message가 Origin 거부 사유인지 확인한다.
}

@Test // 허용 목록 밖 Origin 거부를 별도 case로 검증한다.
void 허용되지_않은_Origin의_세션_API_요청은_거부한다() throws Exception { // 공격자 Origin을 가정한 test다.
    mockMvc.perform(
                    post("/sessions") // Origin 검사가 적용되는 로그인 POST를 만든다.
                            .header(HttpHeaders.ORIGIN, "http://attacker.example") // 허용 목록에 없는 Origin을 넣는다.
                            .contentType(MediaType.APPLICATION_JSON) // JSON body임을 지정한다.
                            .content("{}") // Controller 검증보다 Origin 검사가 먼저 실행되는지 확인할 body다.
            )
            .andExpect(status().isForbidden()); // Spring CORS 또는 interceptor가 허용 목록 불일치로 HTTP 403을 반환하는지 확인한다.
}
```

### JWT Filter 핵심

```java
@Override // 부모 OncePerRequestFilter의 제외 조건 method를 재정의한다.
protected boolean shouldNotFilter(HttpServletRequest request) { // 현재 요청에서 JWT Filter를 건너뛸지 반환한다.
    String method = request.getMethod(); // GET·POST 같은 HTTP method를 읽는다.
    String path = request.getServletPath(); // application 내부 요청 path를 읽는다.

    return method.equals("OPTIONS") // CORS preflight 요청이면 건너뛴다.
            || method.equals("POST") && path.equals("/users") // 회원가입 요청이면 건너뛴다.
            || method.equals("POST") && path.equals("/sessions") // 로그인 요청이면 건너뛴다.
            || method.equals("POST") && path.equals("/sessions/refresh") // token 재발급 요청이면 건너뛴다.
            || method.equals("DELETE") && path.equals("/sessions"); // 로그아웃 요청이면 건너뛴다.
}
```

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
    authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request)); // 현재 HTTP 요청의 IP·session 관련 세부 정보를 인증 객체에 붙인다.

    SecurityContextHolder.getContext().setAuthentication(authentication); // 현재 요청 Thread의 SecurityContext에 인증을 저장한다.
    filterChain.doFilter(request, response); // 인증된 상태로 다음 Filter와 Controller 실행을 계속한다.
} catch (AuthException | DataNullException e) { // 잘못된 JWT나 현재 사용할 수 없는 사용자를 처리한다.
    SecurityContextHolder.clearContext(); // 중간에 남을 수 있는 인증 정보를 제거한다.
    response.setStatus(HttpServletResponse.SC_UNAUTHORIZED); // 응답 상태를 401로 지정한다.
    response.setContentType("application/json;charset=UTF-8"); // 직접 작성할 body가 UTF-8 JSON임을 지정한다.
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
    try { // hash algorithm 조회에서 발생 가능한 checked exception을 처리한다.
        MessageDigest messageDigest = MessageDigest.getInstance("SHA-256"); // JVM에서 SHA-256 hash 구현을 얻는다.
        byte[] tokenHash = messageDigest.digest( // token 원문을 단방향 hash한 byte 배열을 만든다.
                refreshToken.getBytes(StandardCharsets.UTF_8) // 문자열을 UTF-8 byte로 변환한다.
        );

        return HexFormat.of().formatHex(tokenHash); // hash byte를 DB에 저장하기 쉬운 16진수 문자열로 바꾼다.
    } catch (NoSuchAlgorithmException e) { // 실행 환경에 SHA-256 구현이 없는 비정상 상태를 잡는다.
        throw new IllegalStateException("SHA-256 is not available", e); // 복구하기 어려운 환경 오류로 바꿔 원인 예외와 함께 전파한다.
    }
}
```

### Refresh Session 회전

```java
@Lock(LockModeType.PESSIMISTIC_WRITE) // 조회한 AuthSession row를 Transaction이 끝날 때까지 다른 write 요청이 동시에 변경하지 못하게 잠근다.
Optional<AuthSession> findByRefreshTokenHash( // Refresh Token hash로 session을 찾되 잠금 결과가 없을 수도 있음을 Optional로 반환한다.
        String refreshTokenHash // browser가 보낸 원문을 SHA-256으로 바꾼 조회 조건이다.
);
```

이 Repository method는 `SessionService`의 `@Transactional` 범위 안에서 호출된다. 따라서 method 호출이 끝났다고 바로 lock이 풀리는 것이 아니라 Service Transaction이 commit 또는 rollback될 때 풀린다.

```java
public SessionRefreshResponseDto refreshSession(String refreshToken) { // Cookie에서 받은 Refresh Token으로 새 token 쌍을 만든다.
    if (refreshToken == null || refreshToken.isBlank()) { // Cookie가 없거나 빈 값인지 확인한다.
        throw new AuthException("Invalid_Refresh_Token"); // 사용할 token이 없으면 401 업무 예외를 던진다.
    }

    String refreshTokenHash = refreshTokenProvider.hashRefreshToken(refreshToken); // 원문을 DB에 저장된 형식과 같은 hash로 바꾼다.
    AuthSession authSession = authSessionRepository // Refresh Session repository 조회를 시작한다.
            .findByRefreshTokenHash(refreshTokenHash) // 같은 hash의 session row를 write lock과 함께 찾으며, 경쟁 요청은 여기서 기다린다.
            .orElseThrow(() -> new AuthException("Invalid_Refresh_Token")); // 없으면 401 예외를 지연 생성해 던진다.

    if (!authSession.isActive(LocalDateTime.now())) { // revoke되지 않았고 만료 전인지 검사한다.
        throw new AuthException("Invalid_Refresh_Token"); // 비활성 session이면 재발급을 거부한다.
    }

    if (authSession.getUser().isDeleted() || authSession.getUser().isSuspended()) { // session 소유 사용자의 현재 삭제·정지 상태를 검사한다.
        throw new AuthException("Invalid_Refresh_Token"); // 사용할 수 없는 사용자라면 재발급을 거부한다.
    }

    String newRefreshToken = refreshTokenProvider.createRefreshToken(); // browser에 보낼 새 무작위 원문 token을 만든다.
    String newRefreshTokenHash = refreshTokenProvider.hashRefreshToken( // 새 원문을 DB 저장용 hash로 바꾼다.
            newRefreshToken
    );
    authSession.rotate( // 조회한 JPA entity의 token 정보를 새 값으로 변경한다.
            newRefreshTokenHash, // 기존 hash를 대체할 새 hash다.
            refreshTokenProvider.createExpirationTime() // 새 token의 만료 시각이다.
    );

    String accessToken = jwtProvider.createAccessToken( // 현재 사용자 상태로 새 Access Token을 만든다.
            authSession.getUser().getUserId(), // JWT subject에 넣을 사용자 ID다.
            authSession.getUser().getAuthVersion() // 기존 token 무효화에 사용할 현재 인증 version이다.
    );

    return new SessionRefreshResponseDto( // Controller에 반환할 재발급 결과 DTO를 만든다.
            accessToken, // response body로 전달할 새 Access Token이다.
            newRefreshToken // Controller가 새 HttpOnly Cookie를 만드는 데 쓸 원문 Refresh Token이다.
    );
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

### 프론트 Refresh Token 재발급 공유

```javascript
async function performRefresh() { // 실제 refresh HTTP 요청을 한 번 수행한다.
    const result = await sendRequest( // 공통 요청 함수를 사용해 response와 body를 받는다.
        "/sessions/refresh", // backend의 token 재발급 endpoint다.
        { method: "POST" }, // POST 요청으로 지정한다.
        false, // 만료된 Access Token을 Authorization header에 포함하지 않는다.
    );

    if (!result.response.ok) { // refresh 응답이 200~299가 아닌지 확인한다.
        throw createRequestError(result.response, result.data); // 실패 상태와 body를 프로젝트 Error로 바꿔 던진다.
    }

    if (!result.data?.accessToken) { // 성공 body에 새 Access Token이 실제로 있는지 확인한다.
        throw new Error("액세스 토큰 재발급 응답이 올바르지 않습니다."); // 계약에 맞지 않는 성공 응답도 실패로 처리한다.
    }

    authStorage.setAccessToken(result.data.accessToken); // 새 Access Token을 localStorage에 교체 저장한다.
    return result.data; // 호출자에게 재발급 body를 반환한다.
}

export function refreshAccessToken() { // 동시에 호출돼도 공유할 refresh Promise를 반환한다.
    if (!refreshPromise) { // 진행 중인 재발급이 없는 경우에만 새 요청을 만든다.
        refreshPromise = performRefresh().finally(() => { // 성공·실패 뒤 정리 작업을 붙인 Promise를 공유 변수에 저장한다.
            refreshPromise = null; // 완료 후 다음 재발급이 새 요청을 만들 수 있게 비운다.
        });
    }

    return refreshPromise; // 새 Promise 또는 이미 진행 중인 같은 Promise를 반환한다.
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

### `Authentication`, principal과 credential

```java
new UsernamePasswordAuthenticationToken(email, password)
```

인증 전에는 principal 자리에 사용자 식별자인 email, credentials 자리에 입력 password가 들어간다. `AuthenticationManager.authenticate()`가 성공하면 반환된 `Authentication`의 principal은 `CustomUserDetails`, authorities는 부여된 권한 목록이 된다. JWT Filter에서는 이미 token 검증을 끝낸 뒤 principal·authorities를 넣은 생성자를 사용해 인증된 객체를 직접 만든다.

### `@JsonIgnore`

```java
@JsonIgnore
private String refreshToken;
```

Java 객체 내부에는 값을 유지하지만 Jackson의 JSON 직렬화 대상에서는 제외한다. 따라서 Controller는 getter로 Refresh Token 원문을 읽어 Cookie를 만들 수 있지만 response body에는 이 field가 나타나지 않는다.

### `instanceof` pattern variable

```java
if (!(authVersionClaim instanceof Number authVersion)) {
    throw new AuthException("Invalid_Token");
}
```

`authVersionClaim`이 `Number`인지 검사하면서 맞는 경우 형 변환된 값을 `authVersion` 변수에 연결하는 문법이다. 조건에는 바깥 `!`가 있으므로 Number가 아니면 예외를 던지고, 조건문 다음부터는 Number인 `authVersion`을 사용할 수 있다.

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
10. `SessionResponseDto` 안에 Refresh Token 원문이 있는데도 JSON body에는 포함되지 않는 이유는 무엇인가?
11. `authenticationEntryPoint`, `accessDeniedHandler`, JWT Filter의 catch는 각각 어떤 상황에서 응답을 직접 만드는가?
12. 같은 기존 Refresh Token으로 재발급 요청 두 개가 동시에 들어와도 하나만 성공하도록 만드는 backend 장치는 무엇인가?
13. 현재 세션 Origin 정책에서 header가 없거나 허용 목록에 없는 요청은 각각 어떻게 처리되는가?

## 5.11 오답노트

이 장의 이해 확인에서 틀리거나 핵심이 부족한 문제를 여기에 누적한다.
