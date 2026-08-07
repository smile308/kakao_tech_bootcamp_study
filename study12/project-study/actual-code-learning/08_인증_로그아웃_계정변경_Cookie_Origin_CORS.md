# 08장. 로그아웃·계정 변경·Cookie·Origin/CORS 전체 흐름

이 장은 인증 상태를 **끝내거나 무효화하는 요청**을 읽습니다. 새 token을 발급하는 07장과
달리, 이 장의 핵심은 다음 세 가지입니다.

1. 로그아웃은 현재 Refresh Session을 revoke하고 browser Refresh Cookie를 만료시킵니다.
2. 비밀번호 변경은 User의 `authVersion`을 증가시키고 모든 active Refresh Session을 revoke해
   기존 Access/Refresh 인증을 함께 무효화합니다.
3. 회원 탈퇴는 User를 물리적으로 삭제하지 않고 soft delete한 뒤 Session을 revoke하고
   Cookie를 만료시킵니다. 회원정보 수정은 인증을 무효화하지 않고 User profile field만 변경합니다.

마지막에는 Cookie를 사용하는 session 요청의 Origin 검사와, 모든 API에 적용되는 CORS 설정을
분리해서 설명합니다. `Origin` 검사는 MVC Interceptor가 직접 응답을 끝내는 경로이고,
Controller 호출 중 발생한 Service 예외는 `GlobalExceptionHandler`로 이동한다는 점도 구분합니다.

## 이 장의 실행 지도

### 로그아웃

~~~~text
AppHeader의 로그아웃 클릭
→ authApi.logout()
→ api.request("/sessions", { method: "DELETE" })
→ credentials: "include"로 Refresh Cookie 포함
→ JwtAuthenticationFilter는 DELETE /sessions를 건너뜀
→ SecurityConfig의 DELETE /sessions permitAll
→ SessionOriginInterceptor가 Origin 확인
→ SessionController.deleteSession의 @CookieValue
→ SessionService.deleteSession
→ blank면 아무 작업 없이 종료
→ 원문 token을 hash로 변환
→ AuthSessionRepository.findByRefreshTokenHash
→ 찾은 AuthSession.revoke(now)
→ Controller가 Set-Cookie: maxAge=0 추가
→ 204 응답
→ AppHeader의 finally가 Access Token 삭제·/login 이동
~~~~

### 비밀번호 변경

~~~~text
PasswordEditPage.handleSubmit
→ userApi.updatePassword
→ api.request PATCH /users/password
→ Authorization: Bearer Access Token
→ JwtAuthenticationFilter가 User와 authVersion 검증
→ UserController.setPassword
→ @AuthenticationPrincipal의 userId + @Valid request
→ UserService.setPassword
→ password/passwordCheck 비교
→ 현재 password를 PasswordEncoder.matches로 확인
→ User.changePassword(encodedPassword)
→ authVersion 증가
→ AuthSessionRepository.revokeAllActiveByUser bulk UPDATE
→ UserPasswordResponseDto 반환
→ Controller가 만료 Cookie header 추가
→ 프론트가 Access Token 삭제·/login 이동
~~~~

### 회원정보 수정과 탈퇴

~~~~text
ProfileEditPage.handleSubmit
→ userApi.updateProfile
→ PATCH /users
→ UserController.patchUser
→ UserService.patchUser
→ nickname 중복·profile image 검증
→ User.update(nickname, profileImage)
→ 응답 반환
→ 프론트 state 갱신·Toast 표시

ProfileEditPage.handleWithdraw
→ userApi.deleteUser
→ DELETE /users
→ UserController.deleteUser
→ UserService.deleteUser
→ User.delete(soft delete)
→ AuthSessionRepository.revokeAllActiveByUser
→ 만료 Cookie header
→ 프론트 Access Token 삭제·/login 이동
~~~~

## 실행 순서와 코드 읽기 순서

### 실제 runtime 순서

HTTP 요청은 Filter → Interceptor → Controller 순서로 진입하고, Controller가 Service를
호출한 뒤 반환값과 response header를 조합합니다. 프론트는 성공 이후 localStorage와 화면을
정리합니다. 반면 페이지 파일을 읽을 때는 API method와 backend endpoint의 계약을 먼저 보면
전체 흐름을 놓치지 않습니다.

### 이 장에서의 권장 읽기 순서

1. `AppHeader.handleLogout`
2. `PasswordEditPage.handleSubmit`, `ProfileEditPage.handleSubmit`, `handleWithdraw`
3. `authApi.logout`, `userApi.updatePassword/updateProfile/deleteUser`
4. `api.js.request`의 `credentials`, Access Token header, refresh 제외 목록
5. `JwtAuthenticationFilter.shouldNotFilter`와 `SecurityConfig`의 logout 공개 규칙
6. `SessionOriginInterceptor`와 `InterceptorConfig`
7. `SessionController.deleteSession`, `UserController.deleteUser/setPassword/patchUser`
8. `SessionService.deleteSession`, `UserService.deleteUser/setPassword/patchUser`
9. `AuthSession.revoke`, `User.delete/changePassword/update`, Repository bulk update
10. `RefreshCookieProvider.createExpiredRefreshTokenCookie`
11. `CorsOriginProvider`, `WebConfig`
12. 관련 단위·Controller delegation·MockMvc·repository integration test

### 코드 block 표기 규칙

- `코드 원문`은 현재 source의 해당 class/method를 그대로 옮긴 것입니다.
- 특정 method만 보여주는 block은 실제 source의 발췌입니다. import, 다른 화면 JSX,
  다른 업무 method를 생략한 경우에는 바로 아래에 생략 이유와 원문 위치를 적습니다.
- 앞 장에서 본 JWT parser·`authStorage`·`ResponseCookie` 기본 문법도 이 흐름에서 다시
  사용되면, 현재 값이 어디서 오고 어디로 전달되는지 다시 설명합니다.

---

## 1. AppHeader가 로그아웃 동작을 시작한다

### 실제 파일

`/Users/miles/Documents/GitHub/KTB4_Miles_Week12_Front/src/components/layout/AppHeader.jsx:62-69`

### 코드 원문: `handleLogout`

~~~~jsx
async function handleLogout() {
    try {
        await authApi.logout();
    } finally {
        authStorage.removeAccessToken();
        navigate("/login", { replace: true });
    }
}
~~~~

### 코드 바로 아래 설명

- 이 함수는 같은 파일의 JSX에서 로그아웃 `<button onClick={handleLogout}>`에 전달됩니다.
  사용자가 버튼을 클릭하면 React가 event handler로 이 함수를 호출합니다.
- `await authApi.logout()`은 backend의 `DELETE /sessions`가 끝날 때까지 기다립니다.
  이 요청에는 browser가 보관한 HttpOnly Refresh Cookie가 자동으로 포함됩니다.
- `finally`는 `authApi.logout()`이 성공해도, 네트워크 오류나 서버 4xx/5xx로 throw해도
  실행됩니다. 따라서 현재 코드에서는 서버 revoke가 실패해도 프론트 Access Token을 지우고
  `/login`으로 이동합니다. 이것은 “서버에서도 반드시 revoke가 성공했다”는 보장은 아닙니다.
- `authStorage.removeAccessToken()`은 localStorage의 Access Token만 삭제합니다. JavaScript가
  HttpOnly Refresh Cookie를 직접 지우는 것이 아니라, 성공한 logout response의
  `Set-Cookie; Max-Age=0`를 browser가 처리해 Cookie를 만료시킵니다.
- `navigate("/login", { replace: true })`는 React Router로 login 화면으로 이동시키고,
  `replace`는 현재 화면을 history entry로 남기지 않도록 합니다.

### JSX에서 호출되는 버튼의 실제 위치

`/Users/miles/Documents/GitHub/KTB4_Miles_Week12_Front/src/components/layout/AppHeader.jsx:109-115`

~~~~jsx
<button
    type="button"
    className="profile-menu__item profile-menu__logout"
    onClick={handleLogout}
>
    로그아웃
</button>
~~~~

### 코드 바로 아래 설명

- `onClick={handleLogout}`은 함수를 즉시 실행한 결과가 아니라 클릭 시 실행할 함수
  reference를 React에 전달하는 JSX 문법입니다. `onClick={handleLogout()}`로 쓰면 render
  시점에 실행되므로 현재 코드와 의미가 달라집니다.
- `type="button"`은 이 button이 form submit으로 동작하지 않도록 명시합니다.
- 이 장에서 AppHeader의 profile 조회 `useEffect`, menu outside-click effect, 전체 JSX는
  로그아웃 API 흐름에 직접 필요하지 않아 반복하지 않습니다. 현재 코드는 위 button이
  `handleLogout`을 호출한다는 연결만 확인합니다.

---

## 2. 프론트 API module이 logout endpoint를 공통 request에 연결한다

### 실제 파일

`/Users/miles/Documents/GitHub/KTB4_Miles_Week12_Front/src/api/authApi.js:1-17`

### 코드 원문: logout method

~~~~js
import { refreshAccessToken, request } from "./api.js";

export const authApi = {
    logout() {
        return request("/sessions", { method: "DELETE" });
    },

    refresh() {
        return refreshAccessToken();
    },
};
~~~~

### 코드 바로 아래 설명

- `authApi`는 인증 요청을 endpoint 이름으로 묶은 객체입니다. `AppHeader`는 fetch를 직접
  쓰지 않고 `authApi.logout()`만 호출합니다.
- `request("/sessions", { method: "DELETE" })`의 endpoint와 method가 backend
  `SessionController.deleteSession`의 `@RequestMapping("/sessions")` + `@DeleteMapping`과
  연결됩니다.
- `authApi.refresh()`는 07장에서 확인했듯 선언은 있지만 현재 source 검색에서 호출자를
  확인하지 못했습니다. 실제 401 자동 재발급은 `api.js.request`가
  `refreshAccessToken()`을 직접 호출합니다. 이 장의 logout 흐름에서 `refresh()`는 실행되지
  않습니다.

### 공통 `request`에서 logout이 특별히 처리되는 부분

`/Users/miles/Documents/GitHub/KTB4_Miles_Week12_Front/src/api/api.js:5-10,19-39,109-136`

~~~~js
const NO_REFRESH_REQUESTS = new Set([
    "POST /sessions",
    "POST /sessions/refresh",
    "DELETE /sessions",
    "POST /users",
]);

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

export async function request(endpoint, options = {}) {
    let result = await sendRequest(endpoint, options);

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

    if (!result.response.ok) {
        if (result.response.status === 401) {
            clearAuthentication();
        }

        throw createRequestError(result.response, result.data);
    }

    return result.data;
}
~~~~

### 코드 바로 아래 설명

- `DELETE /sessions`가 `NO_REFRESH_REQUESTS`에 포함되어 있으므로 logout이 401을 받아도
  자동으로 Refresh를 재귀 호출하지 않습니다. 로그아웃은 token을 새로 발급하기 위한
  요청이 아니기 때문입니다.
- `sendRequest`의 기본 `includeAccessToken`은 true이므로 현재 localStorage에 Access
  Token이 있으면 `Authorization: Bearer ...`가 붙습니다. 그러나 backend Filter는
  `DELETE /sessions`를 `shouldNotFilter`에서 제외합니다. header가 붙을 수 있어도 이
  endpoint의 서버 진입에 Access Token 검증이 필수라는 뜻은 아닙니다.
- `credentials: "include"`는 cross-origin 요청에서도 Cookie를 포함시키도록 fetch에
  요청합니다. Cookie 값 원문을 JavaScript가 읽는 것이 아니라 browser가 request header에
  자동으로 넣습니다.
- logout response가 204이면 body는 비어 있을 수 있고, `request`는 `readResponseData`가
  null을 반환한 뒤 `response.ok`를 확인하고 null을 호출자에게 돌려줍니다.
- logout 서버 요청이 실패해 `request`가 Error를 던지더라도 AppHeader의 `finally`가 실행되어
  화면과 localStorage를 정리합니다. 서버 Session의 실제 상태는 별도 확인이 필요합니다.

---

## 3. logout은 Filter 인증 없이 Origin 검사 후 Controller로 들어간다

### 3.1 JWT Filter 제외 규칙

`/Users/miles/Documents/GitHub/KTB4_Miles_Week12_Back/src/main/java/kr/adapterz/springdatajpa/auth/JwtAuthenticationFilter.java:28-38`

~~~~java
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
~~~~

### 코드 바로 아래 설명

- `DELETE /sessions`가 true를 반환하므로 `doFilterInternal`의 Bearer 검증·User 조회·
  SecurityContext 생성이 실행되지 않습니다.
- logout은 Cookie의 Refresh Token을 폐기하는 endpoint이므로, 만료된 Access Token이 남아
  있어도 logout 요청 자체는 서버에 도달할 수 있게 만든 구조입니다.
- 이것은 Cookie의 유효성을 검사하지 않는다는 뜻이 아닙니다. 뒤의 MVC Interceptor와
  Controller/Service가 Origin과 Cookie를 처리합니다.

### 3.2 Security authorization 공개 규칙

`/Users/miles/Documents/GitHub/KTB4_Miles_Week12_Back/src/main/java/kr/adapterz/springdatajpa/config/SecurityConfig.java:49-70`

~~~~java
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
~~~~

### 코드 바로 아래 설명

- `permitAll()`은 logout path에서 Security authorization이 인증 객체를 요구하지 않게 합니다.
  `JwtAuthenticationFilter.shouldNotFilter`와 함께 동작해야 만료 Access Token으로도
  logout path가 막히지 않습니다.
- `/users/password`, `DELETE /users`, `PATCH /users`는 위 공개 목록에 없으므로
  `.anyRequest().authenticated()`에 의해 Access Token 인증이 필요합니다.
- `addFilterBefore`는 사용자 Filter의 등록 위치를 정하지만, 특정 request에서 Filter body를
  실행할지는 위 `shouldNotFilter`가 다시 결정합니다.

### 3.3 SessionOriginInterceptor가 Origin을 검사한다

`/Users/miles/Documents/GitHub/KTB4_Miles_Week12_Back/src/main/java/kr/adapterz/springdatajpa/config/SessionOriginInterceptor.java:19-46`

~~~~java
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

    errorResponseWriter.write(
            response,
            org.springframework.http.HttpStatus.FORBIDDEN,
            ApiErrorCode.FORBIDDEN_ORIGIN
    );
    return false;
}

private boolean isCookieSessionRequest(HttpServletRequest request) {
    String method = request.getMethod();
    String path = request.getRequestURI();

    return method.equals("POST") && path.equals("/sessions")
            || method.equals("POST") && path.equals("/sessions/refresh")
            || method.equals("DELETE") && path.equals("/sessions");
}
~~~~

### 코드 바로 아래 설명

- `InterceptorConfig`가 `SessionOriginInterceptor`를 `/sessions`, `/sessions/refresh` path에
  등록합니다. 등록 path에는 method 제한이 없지만, private `isCookieSessionRequest`가
  다시 method와 URI를 검사해 실제로는 세 가지 조합만 허용 목록 검사 대상이 됩니다.
- logout은 `DELETE /sessions`이므로 `Origin` header가 허용 목록에 있어야 Controller까지
  진행합니다. header가 없으면 `corsOriginProvider.isAllowed(null)`이 false입니다.
- 허용 Origin이면 `preHandle`이 true를 반환해 Controller로 넘어갑니다. 불허·누락이면
  `ErrorResponseWriter`가 403 JSON을 직접 작성하고 false를 반환합니다.
- 이 false는 예외를 `GlobalExceptionHandler`로 던지는 방식이 아니라 MVC handler 호출을
  중단하는 방식입니다. 따라서 Origin 오류와 Service의 `AuthException` 응답 경계를
  구분해야 합니다.

### 3.4 Interceptor 등록 위치

`/Users/miles/Documents/GitHub/KTB4_Miles_Week12_Back/src/main/java/kr/adapterz/springdatajpa/config/InterceptorConfig.java:15-22`

~~~~java
@Override
public void addInterceptors(InterceptorRegistry registry) {
    registry.addInterceptor(requestLogInterceptor)
            .addPathPatterns("/**");

    registry.addInterceptor(sessionOriginInterceptor)
            .addPathPatterns("/sessions", "/sessions/refresh");
}
~~~~

### 코드 바로 아래 설명

- `requestLogInterceptor`는 모든 MVC path에 등록됩니다.
- `sessionOriginInterceptor`는 두 path에만 등록됩니다. `DELETE /sessions`가 `/sessions`
  path와 일치하므로 logout에도 적용됩니다.
- 이 설정은 Jwt Filter보다 뒤의 MVC 단계에 해당합니다. Filter에서 이미 직접 401을 쓰고
  chain을 끝낸 요청은 MVC Interceptor까지 도달하지 않을 수 있습니다.

---

## 4. SessionController가 Cookie를 Service로 전달하고 만료 Cookie를 응답한다

### 실제 파일

`/Users/miles/Documents/GitHub/KTB4_Miles_Week12_Back/src/main/java/kr/adapterz/springdatajpa/controller/SessionController.java:62-79`

### 코드 원문: logout method

~~~~java
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
~~~~

### 코드 바로 아래 설명

- class의 `@RequestMapping("/sessions")`와 `@DeleteMapping`이 합쳐져
  `DELETE /sessions`가 됩니다.
- `@CookieValue(required = false)`는 Refresh Cookie가 없는 경우에도 Controller method가
  호출되도록 하고, `refreshToken`을 null로 받을 수 있게 합니다. Service는 null/blank를
  no-op으로 처리합니다.
- `sessionService.deleteSession(refreshToken)`이 정상적으로 반환된 뒤에만 만료 Cookie를
  header에 추가합니다. Service에서 처리 중 예외가 발생하면 이 `addHeader`와 `return`은
  실행되지 않습니다.
- `createExpiredRefreshTokenCookie()`는 같은 Cookie 이름·path·보안 속성에 빈 값을 넣고
  `maxAge(Duration.ZERO)`를 설정한 새 `ResponseCookie` 객체를 만듭니다. 기존 서버 객체를
  지우는 호출이 아니라, browser에게 기존 Cookie를 즉시 삭제하라는 Set-Cookie response입니다.
- method가 `void`이고 `@ResponseStatus(NO_CONTENT)`이므로 성공 응답 body 없이 204 status를
  사용합니다. Cookie 삭제 정보는 body가 아니라 response header에 있습니다.

---

## 5. SessionService가 logout Session 하나를 revoke한다

### 실제 파일

`/Users/miles/Documents/GitHub/KTB4_Miles_Week12_Back/src/main/java/kr/adapterz/springdatajpa/service/SessionService.java:24-27,72-81`

### 코드 원문: logout method

~~~~java
@Service
@RequiredArgsConstructor
@Transactional
public class SessionService {

    private final RefreshTokenProvider refreshTokenProvider;
    private final AuthSessionRepository authSessionRepository;

    public void deleteSession(String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) {
            return;
        }

        String refreshTokenHash = refreshTokenProvider.hashRefreshToken(refreshToken);
        authSessionRepository
                .findByRefreshTokenHash(refreshTokenHash)
                .ifPresent(authSession -> authSession.revoke(LocalDateTime.now()));
    }
}
~~~~

### 코드 바로 아래 설명

- 실제 `SessionService`에는 login·refresh에 필요한 field도 있지만 이 block에서는 logout
  method가 사용하는 두 dependency와 class annotation만 표시했습니다. 전체 class의 다른
  method는 05·07장에서 실제 source와 함께 설명했습니다.
- null은 Cookie가 request에 없을 때, blank는 빈 문자열·공백만 있는 Cookie 값일 때입니다.
  현재 logout은 이런 입력을 오류로 만들지 않고 바로 return합니다. 그래도 Controller는
  이후 만료 Cookie를 내려 browser state를 정리합니다.
- 유효한 원문이 있으면 07장에서 본 것처럼 SHA-256 hash를 만들고, 같은 hash의
  `AuthSession`을 repository에서 찾습니다.
- repository 결과가 `Optional.empty()`면 `ifPresent` lambda가 실행되지 않습니다. 존재하는
  row만 `authSession.revoke(LocalDateTime.now())`로 폐기합니다.
- `revoke`는 현재 `revokedAt`이 null일 때만 시간을 기록합니다. 이미 폐기된 row를 다시
  호출해도 최초 폐기 시각을 덮어쓰지 않습니다.
- method는 별도의 `save`를 호출하지 않습니다. repository가 반환한 managed entity의
  `revokedAt`을 바꾸면 class-level `@Transactional` commit 시 JPA dirty checking으로
  UPDATE가 반영될 수 있습니다.
- 현재 code는 “Cookie가 없거나 DB에 해당 session이 없음”을 logout 오류로 만들지 않습니다.
  browser가 이미 Cookie를 지웠거나 오래된 token을 보낸 경우에도 만료 Cookie 응답은 계속
  내려갑니다.

---

## 6. 만료 Cookie Provider는 browser 상태를 삭제한다

### 실제 파일

`/Users/miles/Documents/GitHub/KTB4_Miles_Week12_Back/src/main/java/kr/adapterz/springdatajpa/auth/RefreshCookieProvider.java:9-46`

### 코드 원문: 설정 생성과 만료 Cookie

~~~~java
@Component
public class RefreshCookieProvider {

    public static final String COOKIE_NAME = "refreshToken";

    private final Duration refreshExpiration;
    private final boolean secure;
    private final String cookiePath;

    public RefreshCookieProvider(
            @Value("${jwt.refresh-expiration-millis}") long refreshExpirationMillis,
            @Value("${jwt.refresh-cookie-secure}") boolean secure,
            @Value("${jwt.refresh-cookie-path}") String cookiePath
    ) {
        this.refreshExpiration = Duration.ofMillis(refreshExpirationMillis);
        this.secure = secure;
        this.cookiePath = cookiePath;
    }

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
~~~~

### 코드 바로 아래 설명

- 생성자는 YAML property 세 개를 읽어 Cookie builder의 field를 초기화합니다. `@Value`의
  `${...}` key가 현재 profile에서 해결되지 않으면 Bean 생성 단계에서 문제가 생길 수 있습니다.
- `COOKIE_NAME`은 Controller의 `@CookieValue`와 같은 `refreshToken` 문자열을 공유합니다.
  이름이 같아야 browser가 기존 Cookie를 찾아 교체·삭제할 수 있습니다.
- `ResponseCookie.from`은 응답에 보낼 새 Cookie 객체를 만드는 builder 시작점입니다.
  기존 Cookie를 서버 메모리에서 직접 삭제하는 API가 아닙니다.
- 만료 Cookie도 원래 Cookie와 `httpOnly`, `secure`, `sameSite`, `path`를 맞춰야 합니다.
  특히 이름이나 path가 다르면 browser가 기존 Cookie 대신 다른 Cookie를 만들거나 기존 값을
  삭제하지 못할 수 있습니다.
- `maxAge(Duration.ZERO)`는 browser가 수명을 0으로 해석해 즉시 제거하도록 하는 설정입니다.
  DB의 `AuthSession.revokedAt` 변경과 browser Cookie 삭제는 서로 다른 상태 저장소의 작업이며,
  Controller가 두 작업의 결과를 같은 HTTP 응답 흐름에서 조합합니다.

### profile별 Cookie path 확인

`/Users/miles/Documents/GitHub/KTB4_Miles_Week12_Back/src/main/resources/application-local.yaml:29-35`
와
`/Users/miles/Documents/GitHub/KTB4_Miles_Week12_Back/src/main/resources/application-prod.yaml:31-38`

~~~~yaml
# application-local.yaml
jwt:
  refresh-cookie-secure: false
  refresh-cookie-path: /sessions

# application-prod.yaml
jwt:
  refresh-cookie-secure: ${JWT_REFRESH_COOKIE_SECURE:true}
  refresh-cookie-path: /api/sessions
~~~~

### 코드 바로 아래 설명

- local profile에서는 HTTP localhost 개발을 고려해 `secure: false`, path `/sessions`를 사용합니다.
- prod profile에서는 `JWT_REFRESH_COOKIE_SECURE` 환경변수의 값을 사용하고 기본값은 true이며,
  path는 `/api/sessions`입니다.
- 이 path 차이는 backend Controller의 Java mapping 이름만으로 결정되는 것이 아니라,
  profile·reverse proxy가 외부 URL을 어떻게 구성하는지와 함께 맞아야 합니다. 현재 source만으로
  배포 Nginx가 `/api`를 어떻게 전달하는지까지 이 장에서 단정하지 않습니다.

---

## 7. 비밀번호 변경은 Access Token과 Refresh Session을 모두 무효화한다

### 7.1 페이지가 입력을 검증하고 API를 호출한다

### 실제 파일

`/Users/miles/Documents/GitHub/KTB4_Miles_Week12_Front/src/pages/user/PasswordEditPage.jsx:86-112`

### 코드 원문: `handleSubmit`

~~~~jsx
async function handleSubmit(event) {
    event.preventDefault();
    if (!validate()) {
        return;
    }

    try {
        setIsSubmitting(true);
        await userApi.updatePassword(form);
        authStorage.removeAccessToken();
        navigate("/login", { replace: true });
    } catch (error) {
        if (hasErrorCode(error, "INVALID_CURRENT_PASSWORD")) {
            setErrors((previous) => ({
                ...previous,
                currentPassword: getErrorMessage(error),
            }));
        } else {
            setErrors((previous) => ({
                ...previous,
                passwordCheck: getErrorMessage(error, "비밀번호 수정에 실패했습니다."),
            }));
        }
    } finally {
        setIsSubmitting(false);
    }
}
~~~~

### 코드 바로 아래 설명

- `event.preventDefault()`는 form submit의 browser 기본 페이지 이동을 막고 React 함수가
  입력 흐름을 관리하게 합니다.
- `validate()`가 false이면 API를 호출하지 않고 return합니다. 현재 페이지의 client 검증은
  `currentPassword`, 새 password 형식, passwordCheck 일치 여부를 검사합니다.
- `userApi.updatePassword(form)`은 PATCH request를 공통 `request`로 보냅니다. 이 endpoint는
  `NO_REFRESH_REQUESTS`에 없으므로 Access Token이 만료됐다면 07장의 자동 Refresh 후
  원래 요청이 재시도될 수 있습니다.
- 서버 성공 뒤에만 localStorage Access Token을 삭제하고 login으로 이동합니다. 성공 response
  자체에서 backend가 Cookie를 만료시키므로 browser와 localStorage를 각각 정리합니다.
- `INVALID_CURRENT_PASSWORD`는 현재 password 검증 실패를 입력 field에 표시합니다. 그 외
  error는 passwordCheck field에 fallback message를 표시합니다. 이 catch는 서버 DB가
  변경되지 않았음을 직접 증명하지 않고, response error code에 따른 화면 처리를 합니다.
- `finally`의 `setIsSubmitting(false)`는 성공·실패 모두에서 submit loading state를 끕니다.

### API module

`/Users/miles/Documents/GitHub/KTB4_Miles_Week12_Front/src/api/userApi.js:15-20`

~~~~js
updatePassword(payload) {
    return request("/users/password", {
        method: "PATCH",
        body: JSON.stringify(payload),
    });
},
~~~~

### 코드 바로 아래 설명

- `payload`는 페이지의 `form` state에서 온 객체입니다. `JSON.stringify`가 JavaScript
  object를 HTTP JSON body 문자열로 바꿉니다.
- API module은 Access Token을 직접 읽지 않습니다. `request`가 `authStorage`에서 매번
  최신 token을 읽어 `Authorization` header를 만듭니다.

### 7.2 Controller가 인증 User와 request DTO를 받는다

`/Users/miles/Documents/GitHub/KTB4_Miles_Week12_Back/src/main/java/kr/adapterz/springdatajpa/controller/UserController.java:50-59`

~~~~java
@PatchMapping("/password")
public UserPasswordResponseDto setPassword(
        @AuthenticationPrincipal CustomUserDetails userDetails,
        @Valid @RequestBody UserPasswordRequestDto request,
        HttpServletResponse response
){
    UserPasswordResponseDto responseDto = userService.setPassword(
            userDetails.getUserId(),
            request
    );
    expireRefreshTokenCookie(response);
    return responseDto;
}
~~~~

### 코드 바로 아래 설명

- class의 `/users` mapping과 method `/password`가 합쳐져 PATCH `/users/password`가 됩니다.
- `@AuthenticationPrincipal`은 06장에서 Filter가 SecurityContext에 저장한
  `CustomUserDetails`를 parameter로 주입합니다. 페이지가 보낸 userId가 아니라 서버가
  인증한 principal의 `getUserId()`를 Service 입력으로 사용합니다.
- `@Valid @RequestBody`는 JSON body를 `UserPasswordRequestDto`로 역직렬화하고 DTO validation을
  수행합니다. validation 실패는 Service까지 가지 않고 전역 validation handler 경로로 갑니다.
- Service가 정상 종료하면 private `expireRefreshTokenCookie(response)`가 만료 Cookie를
  response에 추가합니다. body DTO와 Cookie header가 서로 다른 경로로 응답에 들어갑니다.
- 이 method가 success response를 반환하기 전에 Service 예외가 발생하면 Cookie 만료 호출도
  실행되지 않습니다.

### 7.3 Service가 password 검증·version 증가·Session 일괄 revoke를 수행한다

`/Users/miles/Documents/GitHub/KTB4_Miles_Week12_Back/src/main/java/kr/adapterz/springdatajpa/service/UserService.java:18-24,93-114`

### 코드 원문: 선언과 `setPassword`

~~~~java
@Service
@RequiredArgsConstructor
@Transactional
public class UserService {
    private final UserRepository userRepository;
    private final AuthSessionRepository authSessionRepository;
    private final PasswordEncoder passwordEncoder;

    public UserPasswordResponseDto setPassword(
            Long loginUserId,
            UserPasswordRequestDto request
    ){
        if (!request.getPassword().equals(request.getPasswordCheck())) {
            throw new InvalidRequestException("Invalid_Password");
        }

        User user = getLoginUser(loginUserId);

        if (!passwordEncoder.matches(request.getCurrentPassword(), user.getPassword())) {
            throw new InvalidRequestException("Invalid_Current_Password");
        }

        UserPasswordResponseDto userPasswordResponseDto = new UserPasswordResponseDto();

        user.changePassword(passwordEncoder.encode(request.getPassword()));
        authSessionRepository.revokeAllActiveByUser(user, LocalDateTime.now());
        return userPasswordResponseDto;
    }

    private User getLoginUser(Long loginUserId) {
        return userRepository.findByUserIdAndDeletedFalse(loginUserId)
                .orElseThrow(() -> new DataNullException("No_User"));
    }
}
~~~~

### 코드 바로 아래 설명

- `@Transactional`은 User entity 변경과 Session bulk update를 하나의 transaction 흐름에
  포함시킵니다. 정상 종료 시 함께 commit되고, runtime exception이 중간에 나면 현재
  transaction 경계에서 정상 commit이 진행되지 않습니다.
- `request.getPassword()`와 `getPasswordCheck()`가 다르면 User를 조회하기도 전에 예외가
  발생합니다. 현재 password를 변경하거나 Session을 revoke하지 않습니다.
- `getLoginUser(loginUserId)`는 Filter의 principal에서 전달받은 ID로
  `findByUserIdAndDeletedFalse`를 호출합니다. 삭제된 User는 조회되지 않으며
  `DataNullException("No_User")`이 됩니다.
- `PasswordEncoder.matches(raw, encoded)`의 첫 번째 값은 사용자가 입력한 현재 password,
  두 번째 값은 DB에 저장된 hash입니다. 둘이 다르면 새 hash를 만들지 않습니다.
- 성공하면 `passwordEncoder.encode`로 새 password를 hash로 만들고
  `user.changePassword`를 호출합니다. 원문 password를 User field나 DB에 저장하지 않습니다.
- `changePassword`는 password field를 바꾸고 `authVersion++`을 수행합니다. 이후 새 보호
  요청에서 06장의 Filter가 JWT claim의 이전 version과 DB의 새 version을 비교해 old Access
  Token을 거부합니다.
- `revokeAllActiveByUser`는 해당 User의 모든 `revokedAt IS NULL` AuthSession을 bulk UPDATE
  합니다. 따라서 현재 tab의 Cookie뿐 아니라 다른 device/browser의 active Refresh Session도
  서버에서 더 이상 사용할 수 없게 됩니다.

---

## 8. 회원 탈퇴는 soft delete·Session revoke·Cookie 만료를 함께 수행한다

### 8.1 탈퇴 화면의 요청과 localStorage 정리

`/Users/miles/Documents/GitHub/KTB4_Miles_Week12_Front/src/pages/user/ProfileEditPage.jsx:134-143`

~~~~jsx
async function handleWithdraw() {
    try {
        await userApi.deleteUser();
        authStorage.removeAccessToken();
        navigate("/login", { replace: true });
    } catch (error) {
        setIsWithdrawModalOpen(false);
        window.alert(getErrorMessage(error, "회원 탈퇴에 실패했습니다."));
    }
}
~~~~

### 코드 바로 아래 설명

- 이 함수는 확인 modal의 `onConfirm={handleWithdraw}`에서 호출됩니다. 사용자가 탈퇴를
  확인한 뒤에만 `DELETE /users`가 시작됩니다.
- 성공하면 backend가 User soft delete와 Session revoke를 끝낸 뒤 response를 반환합니다.
  그 다음 localStorage Access Token을 삭제하고 login으로 이동합니다.
- logout과 달리 이 함수에는 `finally`가 없습니다. 서버 탈퇴 request가 실패하면 localStorage를
  지우거나 login으로 이동하지 않고 modal을 닫은 뒤 alert만 표시합니다.
- Cookie는 JavaScript가 직접 삭제하지 않습니다. 성공한 UserController response의 만료
  Cookie header를 browser가 처리합니다.

### API와 Controller

`/Users/miles/Documents/GitHub/KTB4_Miles_Week12_Front/src/api/userApi.js:22-24`

~~~~js
deleteUser() {
    return request("/users", { method: "DELETE" });
},
~~~~

`/Users/miles/Documents/GitHub/KTB4_Miles_Week12_Back/src/main/java/kr/adapterz/springdatajpa/controller/UserController.java:33-41,61-66`

~~~~java
@DeleteMapping
@ResponseStatus(HttpStatus.NO_CONTENT)
public UserDeleteResponseDto deleteUser(
        @AuthenticationPrincipal CustomUserDetails userDetails,
        HttpServletResponse response
){
    UserDeleteResponseDto responseDto = userService.deleteUser(userDetails.getUserId());
    expireRefreshTokenCookie(response);
    return responseDto;
}

private void expireRefreshTokenCookie(HttpServletResponse response) {
    response.addHeader(
            HttpHeaders.SET_COOKIE,
            refreshCookieProvider.createExpiredRefreshTokenCookie().toString()
    );
}
~~~~

### 코드 바로 아래 설명

- `DELETE /users`는 SecurityConfig의 공개 목록에 없으므로 Access Token Filter와
  `.anyRequest().authenticated()`를 통과해야 합니다.
- Controller는 principal의 ID만 Service에 전달합니다. 탈퇴 대상 userId를 request body나
  URL에서 받지 않는 이유는 현재 로그인 principal과 다른 사용자를 임의로 탈퇴시키지 않기
  위해서입니다.
- `@ResponseStatus(NO_CONTENT)`가 있지만 method는 `UserDeleteResponseDto`를 반환합니다.
  현재 annotation과 반환 타입이 함께 존재하므로 실제 response body 처리 방식은 Spring MVC
  설정과 실행 결과를 추가 확인해야 하며, 문서에서 “body가 반드시 비어 있다”고 단정하지
  않습니다. Cookie header 추가가 핵심 결과입니다.
- `expireRefreshTokenCookie`는 password 변경 Controller와도 공유되는 private helper입니다.
  두 기능 모두 Service 성공 후 같은 만료 Cookie를 response에 추가합니다.

### 8.2 UserService의 soft delete와 session revoke

`/Users/miles/Documents/GitHub/KTB4_Miles_Week12_Back/src/main/java/kr/adapterz/springdatajpa/service/UserService.java:18-24,70-79,111-114`

~~~~java
public UserDeleteResponseDto deleteUser(Long loginUserId){
    UserDeleteResponseDto userDeleteResponseDto = new UserDeleteResponseDto();
    User user = getLoginUser(loginUserId);
    if (user.isSuspended()) {
        throw new ForbiddenException("Suspended_Account");
    }
    user.delete();
    authSessionRepository.revokeAllActiveByUser(user, LocalDateTime.now());
    return userDeleteResponseDto;
}

private User getLoginUser(Long loginUserId) {
    return userRepository.findByUserIdAndDeletedFalse(loginUserId)
            .orElseThrow(() -> new DataNullException("No_User"));
}
~~~~

### 코드 바로 아래 설명

- 현재 User가 없으면 `DataNullException("No_User")`이고, suspended 기준 이상이면
  `ForbiddenException("Suspended_Account")`입니다. 두 예외 모두 `user.delete()` 이전에
  발생하므로 User와 Session이 변경되지 않습니다.
- `user.delete()`는 DB row를 지우지 않고 `deleted=true`, nickname=`"삭제된 유저"`,
  profileImage=null로 바꿉니다. 게시글·댓글의 작성자 관계를 유지하면서 활성 User 조회에서는
  제외할 수 있는 soft delete입니다.
- 이어서 모든 active AuthSession의 `revokedAt`을 bulk UPDATE합니다. 기존 Access Token은
  다음 요청에서 삭제된 User를 `findByUserIdAndDeletedFalse`로 찾지 못해 거부되고, 기존
  Refresh Token은 revoked session이라 `isActive`에서 거부됩니다.

### Entity method

`/Users/miles/Documents/GitHub/KTB4_Miles_Week12_Back/src/main/java/kr/adapterz/springdatajpa/entity/User.java:73-82,92-95`

~~~~java
public void update(String nickname, String profileImage) {
    this.nickname = nickname;
    this.profileImage = profileImage;
}

public void delete(){
    this.deleted = true;
    this.nickname = "삭제된 유저";
    this.profileImage = null;
}

public void changePassword(String password) {
    this.password = password;
    this.authVersion++;
}
~~~~

### 코드 바로 아래 설명

- `update`는 회원정보 수정에서 nickname과 profileImage만 변경합니다. password,
  `authVersion`, `deleted`를 건드리지 않으므로 회원정보 수정만으로 기존 인증을 무효화하지
  않습니다.
- `delete`는 soft delete 상태와 표시 field를 함께 변경합니다. Entity가 managed 상태인
  UserService transaction에서 변경되므로 별도 `userRepository.save`가 없어도 dirty checking
  대상이 됩니다.
- `changePassword`의 인자에는 UserService가 이미 `PasswordEncoder.encode`한 hash가
  들어옵니다. Entity method는 hash를 다시 encode하지 않고 field를 저장하며 version을 하나
  증가시킵니다.

---

## 9. 회원정보 수정은 인증을 무효화하지 않고 profile state만 바꾼다

### 9.1 프론트 요청

`/Users/miles/Documents/GitHub/KTB4_Miles_Week12_Front/src/pages/user/ProfileEditPage.jsx:102-132`

~~~~jsx
async function handleSubmit(event) {
    event.preventDefault();
    if (!isValidNickname(nickname)) {
        setNicknameError("닉네임은 공백 없이 1~10자로 입력해주세요.");
        return;
    }

    try {
        setIsSubmitting(true);
        setNicknameError("");
        await userApi.updateProfile({
            nickname,
            profileImage: newProfileImage ?? user?.profileImage ?? null,
        });
        setUser((previous) => ({
            ...previous,
            nickname,
            profileImage: newProfileImage ?? previous.profileImage,
        }));
        setNewProfileImage(null);
        showToast("회원정보가 수정되었습니다.");
    } catch (error) {
        if (hasErrorCode(error, "EXISTED_NICKNAME")) {
            setNicknameError(getErrorMessage(error));
        } else {
            setNicknameError(getErrorMessage(error, "회원정보 수정에 실패했습니다."));
        }
    } finally {
        setIsSubmitting(false);
    }
}
~~~~

### 코드 바로 아래 설명

- nickname 형식이 client validation에서 실패하면 API를 보내지 않습니다.
- `newProfileImage ?? user?.profileImage ?? null`은 새로 선택한 이미지가 있으면 그것을
  사용하고, 없으면 기존 profile image를 사용하며 둘 다 null/undefined면 null을 보냅니다.
  `??`는 빈 문자열을 null로 취급하지 않습니다.
- 성공 뒤에는 페이지 내부 `user` state만 갱신하고, Access Token 삭제·Cookie 만료·login
  이동은 하지 않습니다. 회원정보 수정은 현재 code상 인증 version이나 AuthSession을
  변경하지 않는 기능입니다.
- `EXISTED_NICKNAME`만 별도 field error로 보여주고 나머지는 일반 error message를 보여줍니다.

### API와 Controller/Service

`/Users/miles/Documents/GitHub/KTB4_Miles_Week12_Front/src/api/userApi.js:8-13`

~~~~js
updateProfile(payload) {
    return request("/users", {
        method: "PATCH",
        body: JSON.stringify(payload),
    });
},
~~~~

`/Users/miles/Documents/GitHub/KTB4_Miles_Week12_Back/src/main/java/kr/adapterz/springdatajpa/controller/UserController.java:43-49`

~~~~java
@PatchMapping
public UserPatchResponseDto patchUser(
        @AuthenticationPrincipal CustomUserDetails userDetails,
        @Valid @RequestBody UserPatchRequestDto request
){
    return userService.patchUser(userDetails.getUserId(), request);
}
~~~~

`/Users/miles/Documents/GitHub/KTB4_Miles_Week12_Back/src/main/java/kr/adapterz/springdatajpa/service/UserService.java:80-91`

~~~~java
public UserPatchResponseDto patchUser(
        Long loginUserId,
        UserPatchRequestDto request
){
    User user = getLoginUser(loginUserId);

    if (userRepository.existsByNicknameAndDeletedFalseAndUserIdNot(
            request.getNickname(),
            user.getUserId()
    )) {
        throw new InvalidRequestException("Existed_Nickname");
    }

    ImageDataUrlValidator.validateProfileImage(request.getProfileImage());

    user.update(request.getNickname(), request.getProfileImage());
    UserPatchResponseDto userPatchResponseDto = new UserPatchResponseDto();
    return userPatchResponseDto;
}
~~~~

### 코드 바로 아래 설명

- repository query는 현재 로그인 User의 `userId`를 제외하고 같은 nickname을 가진 active
  User가 있는지 확인합니다. 자기 자신의 기존 nickname을 그대로 보내도 중복으로 보지 않는
  이유입니다.
- `ImageDataUrlValidator.validateProfileImage`가 profile image 형식·크기 등을 검사합니다.
  실패하면 `user.update`까지 가지 않습니다.
- `user.update`는 managed entity field만 변경합니다. `revokeAllActiveByUser`,
  `changePassword`, 만료 Cookie helper가 이 흐름에는 없습니다.
- 반환 DTO는 Controller를 거쳐 프론트 `request`의 성공 data가 됩니다. 페이지는 서버가
  반환한 User 전체가 아니라 이미 알고 있는 local state를 갱신합니다.

---

## 10. AuthSessionRepository bulk update가 모든 active Session을 revoke한다

### 실제 파일

`/Users/miles/Documents/GitHub/KTB4_Miles_Week12_Back/src/main/java/kr/adapterz/springdatajpa/repository/AuthSessionRepository.java:20-30`

### 코드 원문

~~~~java
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
~~~~

### 코드 바로 아래 설명

- `@Modifying`은 이 repository method가 SELECT가 아니라 UPDATE query임을 Spring Data
  JPA에 알립니다. 반환값 `int`는 변경된 row 수입니다.
- `@Query`의 Java text block 안 JPQL은 `AuthSession` Entity와 `user`, `revokedAt` field를
  사용합니다. 실제 DB table/column 이름을 직접 쓰지 않습니다.
- `WHERE authSession.user = :user`는 지정한 User의 Session만 대상으로 합니다.
- `revokedAt IS NULL` 조건은 이미 revoke된 session의 최초 시각을 덮어쓰지 않게 합니다.
- `clearAutomatically = true`는 bulk update 뒤 persistence context를 정리하고,
  `flushAutomatically = true`는 query 전에 변경 내용을 flush하도록 합니다. bulk query가
  managed entity와 stale state로 충돌할 수 있어 함께 설정된 것입니다.
- 이 method는 UserService의 `@Transactional` 안에서 호출됩니다. 현재 source에는 반환된
  row count를 검사해 별도 예외를 만드는 코드가 없습니다.

---

## 11. CORS 설정과 Origin 검사는 서로 다른 경계다

### 11.1 허용 Origin 목록을 Bean으로 만든다

`/Users/miles/Documents/GitHub/KTB4_Miles_Week12_Back/src/main/java/kr/adapterz/springdatajpa/config/CorsOriginProvider.java:10-31`

~~~~java
@Component
public class CorsOriginProvider {

    private final Set<String> allowedOrigins;

    public CorsOriginProvider(
            @Value("${cors.allowed-origins}") String allowedOrigins
    ) {
        this.allowedOrigins = Arrays.stream(allowedOrigins.split(","))
                .map(String::trim)
                .filter(origin -> !origin.isEmpty())
                .collect(Collectors.toUnmodifiableSet());
    }

    public String[] getAllowedOrigins() {
        return allowedOrigins.toArray(String[]::new);
    }

    public boolean isAllowed(String origin) {
        return origin != null && allowedOrigins.contains(origin);
    }
}
~~~~

### 코드 바로 아래 설명

- `@Value("${cors.allowed-origins}")`는 profile YAML의 comma-separated 문자열을 생성자에
  전달합니다. local의 여러 localhost origin 또는 prod의 `CORS_ALLOWED_ORIGINS` 환경변수가
  이 입력값이 됩니다.
- `split(",")`로 문자열을 나누고 `String::trim`으로 각 origin의 양끝 공백을 제거한 뒤,
  빈 항목을 버리고 변경 불가능한 `Set`으로 수집합니다. `Set`은 같은 origin을 중복 저장하지
  않습니다.
- `getAllowedOrigins()`는 CORS 설정에 필요한 `String[]`로 변환합니다. `toArray(String[]::new)`
  는 배열 생성 함수를 전달하는 Java method reference 문법입니다.
- `isAllowed`는 null을 허용하지 않고 exact string membership만 확인합니다. `https`와
  `http`, port가 다른 localhost는 서로 다른 문자열로 취급됩니다.

### 11.2 WebConfig가 모든 MVC 경로의 CORS policy를 등록한다

`/Users/miles/Documents/GitHub/KTB4_Miles_Week12_Back/src/main/java/kr/adapterz/springdatajpa/config/WebConfig.java:15-27`

~~~~java
@Bean
public WebMvcConfigurer corsConfigurer() {
    return new WebMvcConfigurer() {
        @Override
        public void addCorsMappings(CorsRegistry registry) {
            registry.addMapping("/**")
                    .allowedOrigins(corsOriginProvider.getAllowedOrigins())
                    .allowedMethods("GET", "POST", "PATCH", "DELETE", "OPTIONS")
                    .allowedHeaders("*")
                    .allowCredentials(true);
        }
    };
}
~~~~

### 코드 바로 아래 설명

- `@Bean` method가 익명 `WebMvcConfigurer` 객체를 반환합니다. Spring MVC가 이 callback의
  `addCorsMappings`를 호출해 CORS mapping을 등록합니다.
- `addMapping("/**")`는 모든 MVC endpoint를 대상으로 합니다. 허용 여부는 위 Provider의
  exact origin 목록으로 제한됩니다.
- `allowedMethods`에는 프론트가 사용하는 GET/POST/PATCH/DELETE와 browser preflight용
  OPTIONS가 포함됩니다. `allowedHeaders("*")`는 Authorization·Content-Type 등 요청 header를
  허용합니다.
- `allowCredentials(true)`는 browser가 Cookie를 포함한 cross-origin 요청을 보낼 수 있게
  하는 설정입니다. 이 경우 wildcard `*` origin을 쓰지 않고 명시적 origin 목록을 사용해야
  합니다.
- CORS 설정은 browser가 response를 읽을 수 있는지와 preflight를 허용할지를 다룹니다.
  `SessionOriginInterceptor.isAllowed`는 Cookie session 요청이 실제 Controller로 들어갈지
  별도로 판단합니다. 두 설정이 모두 있다고 해서 서로 같은 검사가 되는 것은 아닙니다.

### profile property 실제 값

`/Users/miles/Documents/GitHub/KTB4_Miles_Week12_Back/src/main/resources/application-local.yaml:34-35`
와
`/Users/miles/Documents/GitHub/KTB4_Miles_Week12_Back/src/main/resources/application-prod.yaml:37-38`

~~~~yaml
# local
cors:
  allowed-origins: http://localhost:5500,http://127.0.0.1:5500,http://localhost:5173,http://127.0.0.1:5173

# prod
cors:
  allowed-origins: ${CORS_ALLOWED_ORIGINS}
~~~~

### 코드 바로 아래 설명

- local은 YAML에 허용 origin 목록이 직접 있습니다.
- prod는 `${CORS_ALLOWED_ORIGINS}` placeholder를 환경변수로 채워야 합니다. 환경변수가
  없을 때의 기본값이 현재 prod YAML에 없으므로, 실제 배포값은 deployment environment가
  제공해야 합니다.
- 이 값은 `CorsOriginProvider` 생성자에 들어가 WebConfig와 SessionOriginInterceptor가
  함께 사용합니다. WebConfig는 배열, Interceptor는 Set membership으로 각각 사용합니다.

---

## 12. Origin 없음·불일치·허용 Origin의 정확한 분기

### 허용된 Origin

~~~~text
Origin = application-local.yaml의 허용 문자열
→ CorsOriginProvider.isAllowed(origin) == true
→ SessionOriginInterceptor.preHandle returns true
→ SessionController 호출
→ logout이면 SessionService.revoke + 만료 Cookie
~~~~

### Origin 없음 또는 불허 Origin

~~~~text
Origin = null 또는 허용 Set에 없음
→ isAllowed == false
→ ErrorResponseWriter.write(403, FORBIDDEN_ORIGIN)
→ preHandle returns false
→ Controller·SessionService는 호출되지 않음
~~~~

### 코드 바로 아래 설명

- 이 검사는 현재 코드상 `POST /sessions`, `POST /sessions/refresh`, `DELETE /sessions`에만
  적용됩니다. `PATCH /users/password`, `DELETE /users`, `PATCH /users`는
  `SessionOriginInterceptor`의 cookie session 대상이 아니며, CORS/WebConfig와 JWT 인증
  규칙으로 처리됩니다.
- Origin이 없다고 항상 모든 API가 차단되는 것은 아닙니다. Interceptor 등록 path와
  `isCookieSessionRequest` 조건을 통과한 Cookie session 요청에서만 차단됩니다.
- 허용 목록에 없는 Origin 요청은 Controller에서 예외를 throw하는 것이 아니라
  `ErrorResponseWriter`가 직접 403 응답을 씁니다. 그래서 `GlobalExceptionHandler`의
  `@ExceptionHandler`가 이 경로의 주체가 아닙니다.
- 이 구조가 server-to-server 요청, curl, Origin header 없는 기존 테스트를 모두 허용하지
  않는다는 의미는 아닙니다. 현재 코드의 정확한 범위는 위 세 method/path 조합입니다.

---

## 13. 이 장의 예외·transaction 흐름

### 정상 흐름

- logout: Cookie가 없어도 no-op 후 만료 Cookie 응답, Cookie가 있으면 대상 session revoke 후
  만료 Cookie 응답
- password 변경: client/DTO/password 검증 성공 → password hash·authVersion 변경 → 모든
  active Session revoke → 만료 Cookie 응답 → 프론트 localStorage 삭제
- 탈퇴: active User 조회·suspended 검사 통과 → soft delete·Session revoke → 만료 Cookie
  응답 → 프론트 localStorage 삭제·login 이동
- 회원정보 수정: nickname/image 검증 통과 → User.update → response DTO → 화면 state 갱신

### 실패 흐름

- Origin 실패: Interceptor가 403을 직접 작성하므로 Controller·Service·Cookie 만료가 실행되지 않음
- password/passwordCheck 불일치: User 조회·DB 변경 전에 `Invalid_Password`
- 현재 password 불일치: `Invalid_Current_Password`, 새 hash·version·Session revoke 없음
- 없는/deleted User: `No_User`
- suspended User 탈퇴: `Suspended_Account`, soft delete·Session revoke 없음
- nickname 중복: `Existed_Nickname`, `User.update` 전 예외
- profile image 오류: `ImageDataUrlValidator`가 예외를 던지고 User update 전 종료
- 서버 API 오류: Password page는 field error를 표시하고, Profile page는 nickname error/alert를
  표시하며, localStorage 이동 여부는 각 page의 `catch/finally` 코드가 결정

### transaction의 의미

- `UserService`는 class-level `@Transactional`이므로 User entity 변경과
  `revokeAllActiveByUser` bulk update가 같은 Service 호출 transaction에 들어갑니다.
- `SessionService.deleteSession`도 class-level transaction 안에서 managed `AuthSession.revoke`
  변경을 수행합니다.
- Controller가 response header를 추가하는 것은 DB commit 이후의 MVC 응답 조립 단계입니다.
  Service가 예외로 끝나면 Controller의 `expireRefreshTokenCookie`가 실행되지 않습니다.
- Cookie browser state와 DB state는 하나의 DB transaction으로 묶인 동일 저장소가 아닙니다.
  서버 DB 변경이 성공한 뒤 HTTP 응답 전송이 실패하거나, 반대로 프론트가 localStorage를
  지우는 시점에 서버 요청이 실패하는 경계가 있을 수 있습니다. 현재 source에는 이 두 저장소
  사이의 분산 transaction을 보장하는 코드는 없습니다.

---

## 14. 테스트가 확인하는 범위와 확인되지 않는 범위

### 14.1 Controller delegation

`/Users/miles/Documents/GitHub/KTB4_Miles_Week12_Back/src/test/java/kr/adapterz/springdatajpa/controller/ControllerDelegationTest.java:183-224`

테스트 `UserController는_사용자_작업을_전달하고_인증변경시_Cookie를_만료시킨다`는 다음을
확인합니다.

- `deleteUser`, `patchUser`, `setPassword`가 principal의 User ID와 request를 UserService에 전달
- delete/password Controller가 `createExpiredRefreshTokenCookie()`를 호출
- 두 Controller method가 `HttpHeaders.SET_COOKIE`를 같은 servlet response에 추가

같은 파일의 `SessionController는_refresh_Cookie를_발급하고_만료시킨다`는 logout Controller가
Service에 token을 전달하고 만료 Cookie header를 추가하는 위임도 확인합니다.
이것은 Controller method를 mock으로 직접 호출하는 테스트이며, 실제 MVC annotation binding,
browser Cookie 저장, Filter·Interceptor 순서를 증명하지 않습니다.

### 14.2 UserService Mockito 테스트

`/Users/miles/Documents/GitHub/KTB4_Miles_Week12_Back/src/test/java/kr/adapterz/springdatajpa/service/UserServiceTest.java`

현재 source에서 이 장과 직접 연결되는 테스트입니다.

- password/passwordCheck 불일치 시 `Invalid_Password`와 downstream 호출 없음
- 현재 password 불일치 시 encoded password·authVersion이 유지됨
- password 일치 시 encoded password가 저장되고 authVersion이 1 증가
- 탈퇴 대상 User 없음 시 `No_User`
- 탈퇴 시 `deleted`, masked nickname, null profileImage
- suspended User 탈퇴 시 `Suspended_Account`와 변경 없음
- 다른 User의 nickname 중복 및 자기 ID 제외 nickname 수정
- 회원정보 수정 시 User nickname/profile image 변경

이 테스트들은 `MockitoExtension`과 mock repository/encoder를 사용합니다. 따라서 실제
transaction commit, bulk UPDATE SQL, Cookie header, Spring Security principal 주입은
증명하지 않습니다.

### 14.3 Entity·Repository integration 테스트

`/Users/miles/Documents/GitHub/KTB4_Miles_Week12_Back/src/test/java/kr/adapterz/springdatajpa/entity/UserTest.java`

- `User.delete`가 soft delete와 표시 field 변경을 수행하는지
- `User.changePassword`가 password와 authVersion을 바꾸는지
- suspension count 기준이 10인지

`/Users/miles/Documents/GitHub/KTB4_Miles_Week12_Back/src/test/java/kr/adapterz/springdatajpa/repository/UserSoftDeleteIntegrationTest.java`

- deleted User가 `findByUserIdAndDeletedFalse`, `findByEmailAndDeletedFalse`에서 제외되는지
- 탈퇴한 작성자의 게시글은 남고, 작성자 nickname/profile image가 masking되는지

현재 이 장의 source 검색에서 `revokeAllActiveByUser`의 실제 DB bulk update 결과를 직접
검증하는 통합 테스트는 확인되지 않았습니다.

### 14.4 Origin/CORS MockMvc 테스트

`/Users/miles/Documents/GitHub/KTB4_Miles_Week12_Back/src/test/java/kr/adapterz/springdatajpa/config/SecurityConfigTest.java:45-88`

현재 테스트는 다음을 확인합니다.

- 인증 없이 `/posts` 접근 시 401 `UNAUTHORIZED`
- 허용 Origin이 있는 `POST /sessions`가 Security 인증 없이 진입해 DTO validation 단계의
  400까지 도달
- Origin이 없는 `POST /sessions`가 403 `FORBIDDEN_ORIGIN`
- 불허 Origin의 `POST /sessions`가 403

현재 테스트에는 `DELETE /sessions`와 `POST /sessions/refresh`의 Origin 없음·불일치 조합,
실제 browser CORS preflight, `allowCredentials(true)`와 Cookie 저장을 직접 확인하는
테스트가 없습니다. 그러므로 그 동작을 테스트 통과 사실로 표현하지 않습니다.

### 14.5 프론트 테스트

현재 source/test inventory에서 `AppHeader.handleLogout`, `PasswordEditPage`의 finally,
`ProfileEditPage.handleWithdraw`, browser `Set-Cookie` 저장을 자동화하는 frontend test는
확인되지 않았습니다. 문서에서 이 화면 코드의 정상·실패 분기는 실제 source 읽기에 근거한
설명이며, 실행된 test 결과가 아닙니다.

---

## 15. 처음 등장하거나 이 장에서 다시 연결하는 문법

- JavaScript `finally`: Promise가 성공·실패해도 실행되는 정리 구간입니다. AppHeader logout은
  서버 요청 실패 여부와 무관하게 Access Token 삭제·login 이동을 수행합니다.
- `onClick={handleLogout}`: JSX event prop에 실행할 함수 reference를 전달합니다.
- `@AuthenticationPrincipal`: SecurityContext의 principal을 Controller parameter에 주입합니다.
- `@ResponseStatus(HttpStatus.NO_CONTENT)`: Controller의 기본 성공 HTTP status를 204로 지정합니다.
- `@Modifying` + JPQL `UPDATE`: SELECT가 아닌 bulk DML query를 실행하고, 반환 int는 변경 row 수입니다.
- Java `Set`·`String::trim`·`Collectors.toUnmodifiableSet`: origin 문자열을 정리·중복 제거·변경 불가능 collection으로 만드는 문법입니다.
- `allowCredentials(true)`: cross-origin fetch에서 Cookie 같은 credentials를 허용하는 CORS 설정입니다.
- JPA dirty checking: managed User/AuthSession field 변경을 transaction flush/commit 때 DB update로 반영합니다.

앞 장에서 설명한 JWT parser, `authStorage`, `@CookieValue`, `ResponseCookie`,
`SecurityContext`도 이 장의 현재 입력·반환·무효화 지점에 다시 연결했습니다. 특히
“localStorage 삭제 = Refresh Cookie 삭제”가 아니며, “CORS 허용 = Origin Interceptor 통과”가
아니라는 점을 분리해 읽어야 합니다.

---

## 16. 다음 문서와 스킵 범위

### 다음 학습 시작점

다음 문서는 `08-1_인증_백그라운드정리_요청로그_테스트범위.md`입니다. 여기서 HTTP 요청과
분리된 `AuthSessionCleanupScheduler`, `RequestLogInterceptor`, 스케줄 설정값, 인증 테스트
파일 전체 지도를 정리합니다. 이 8장에서는 해당 파일의 전체 원문을 반복하지 않습니다.

### 이 장에서 반복하지 않은 코드

- `JwtProvider`·`JwtAuthenticationFilter`의 JWT claim/parser 전체: 06장에서 전체 흐름을 읽었고,
  이 장에서는 logout이 Filter를 건너뛰며 password/delete/profile 수정은 보호 요청이라는
  path 차이만 다시 확인했습니다.
- `SessionService.refreshSession`·`AuthSession.rotate`: 07장의 Refresh rotation과 다른
  logout method의 `revoke`만 이 장에서 다시 설명했습니다.
- `ErrorResponseWriter`와 `GlobalExceptionHandler` 전체: 04·05·07장에서 직접 응답 경계와
  Controller 예외 경계를 설명했으며, 이 장에서는 Origin 403과 Service 실패의 차이만 연결했습니다.
- AppHeader의 profile 조회 effect, ProfileEditPage의 이미지 파일 변환, 공통 form JSX/CSS:
  로그아웃·계정 상태 무효화에 필요한 호출부가 아니므로 화면 표현과 입력 세부는 해당 프론트
  문서에서 확인합니다.

### 현재 흐름에서 사용되지 않거나 직접 검증되지 않는 항목

- `authApi.refresh()`는 선언되어 있으나 현재 source 검색에서 호출자를 확인하지 못했습니다.
- 실제 browser의 HttpOnly Cookie 저장·삭제와 CORS preflight는 현재 test inventory에서 확인되지 않았습니다.
- 여러 backend instance나 여러 browser tab 사이의 logout·Cookie 일관성을 보장하는 분산
  transaction/lock 코드는 현재 source에서 확인되지 않습니다.

## 진행 상태

- 공식 파일 진행률: **52/214 (약 24.3%)**
- 이번 문서에서 새로 집계한 파일: **0개**. 인증 관련 파일은 04~07장에서 이미 공식 집계했고,
  이 문서는 로그아웃·계정 변경·Origin/CORS 실행 흐름으로 재배치해 연결했습니다.
- 이번 문서에서 대조한 핵심 파일: `AppHeader.jsx`, `PasswordEditPage.jsx`, `ProfileEditPage.jsx`,
  `authApi.js`, `userApi.js`, `api.js`, `JwtAuthenticationFilter.java`, `SecurityConfig.java`,
  `SessionOriginInterceptor.java`, `InterceptorConfig.java`, `SessionController.java`,
  `UserController.java`, `SessionService.java`, `UserService.java`, `User.java`,
  `AuthSessionRepository.java`, `RefreshCookieProvider.java`, `CorsOriginProvider.java`,
  `WebConfig.java`, `ControllerDelegationTest.java`, `UserServiceTest.java`, `UserTest.java`,
  `UserSoftDeleteIntegrationTest.java`, `SecurityConfigTest.java`
- 문서 작성 상태: **완료**
- 사용자 이해 checkpoint: `logout / password 변경 / 탈퇴 / profile 수정`의 서버 상태·Cookie·localStorage 차이 확인 대기
- 다음 학습 시작점: `08-1_인증_백그라운드정리_요청로그_테스트범위.md`
- 실행하지 않은 검증: backend/frontend test, Spring context, 실제 DB transaction, browser Cookie/CORS, Redis, Docker, 배포
