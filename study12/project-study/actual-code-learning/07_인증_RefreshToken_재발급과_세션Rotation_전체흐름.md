# 07장. Refresh Token 재발급과 세션 rotation 전체 흐름

이 장은 Access Token이 만료되어 보호 API가 `401`을 반환한 순간부터, 브라우저가
새 Access Token으로 원래 요청을 다시 보내기까지를 한 흐름으로 읽습니다.

이 장에서 말하는 `Refresh Token rotation`은 기존 Refresh Token을 단순히 다시 보내는
것이 아니라, 서버가 기존 token의 hash를 조회한 뒤 **새 원문 token과 새 hash로 같은
`AuthSession` row를 교체하는 과정**입니다. 새 원문 token은 응답 body가 아니라
`Set-Cookie` header로 브라우저에 전달됩니다.

## 먼저 보는 전체 실행 지도

~~~~text
보호 API 요청
→ api.js.request가 401 응답을 받음
→ refresh 제외 요청이 아니면 refreshAccessToken 호출
→ 모듈 전역 refreshPromise가 같은 재발급 Promise를 공유
→ POST /sessions/refresh (Authorization header 없음, Cookie는 포함)
→ JwtAuthenticationFilter는 /sessions/refresh를 건너뜀
→ SecurityConfig가 해당 경로를 permitAll로 통과시킴
→ SessionOriginInterceptor가 Origin을 확인
→ SessionController.refreshSession이 Cookie의 refreshToken을 읽음
→ SessionService.refreshSession 호출
→ 원문 Refresh Token을 SHA-256 hash로 변환
→ AuthSessionRepository가 hash로 row를 조회하며 PESSIMISTIC_WRITE lock 요청
→ AuthSession이 폐기되지 않았고 만료되지 않았는지 확인
→ User가 탈퇴·정지 상태가 아닌지 확인
→ 새 Refresh Token 생성·hash·만료 시각 계산
→ 같은 AuthSession의 token hash와 만료 시각을 rotate
→ 새 Access Token 생성
→ SessionRefreshResponseDto 반환
→ Controller가 새 Refresh Token을 Set-Cookie header에 추가
→ JSON body에는 accessToken만 노출
→ 브라우저가 Cookie를 교체하고 프론트가 accessToken을 localStorage에 저장
→ api.js가 원래 보호 API를 한 번 재시도
~~~~

이 흐름에는 서로 다른 네 종류의 값이 등장합니다.

| 값 | 생성·보관 위치 | 이 장에서 하는 일 |
|---|---|---|
| 원문 Refresh Token | `RefreshTokenProvider`가 만들고 browser Cookie에 보관 | refresh 요청에서 Cookie로 서버에 전달 |
| Refresh Token hash | `AuthSession.refreshTokenHash` DB column | 원문을 DB에 저장하지 않고 세션 row를 찾는 키로 사용 |
| Access Token | JWT 문자열, browser `localStorage` | 보호 API의 `Authorization` header에 사용 |
| `refreshPromise` | 프론트 `api.js` module 변수 | 동시에 발생한 401들이 재발급 요청 하나를 공유하게 함 |

## 실행 순서와 코드 읽기 순서

### 실제 실행 순서

1. `PostDetailPage` 같은 보호 화면이 `postApi.getPost`를 호출합니다.
2. `api.js.request`가 Access Token을 넣어 원래 API를 호출합니다.
3. 백엔드가 token을 거부해 `401`을 반환하면 `request`가 refresh 대상인지 확인합니다.
4. `refreshAccessToken`이 `/sessions/refresh`를 한 번 호출하고, 완료된 뒤 원래 요청을 다시 보냅니다.
5. 백엔드는 Cookie만으로 Refresh Token을 받습니다. 이 endpoint에는 Access Token 인증이 필요하지 않습니다.
6. Service가 기존 session을 검증하고 같은 row를 새 token 정보로 교체합니다.
7. Controller는 새 Cookie를 response header에 추가하고 DTO를 JSON body로 반환합니다.
8. 프론트는 body의 `accessToken`만 저장하고 원래 요청을 다시 보냅니다.

### 이 장에서 코드를 읽는 순서

실행은 프론트에서 시작하지만, 프론트가 호출하는 함수의 의미를 이해하려면 다음 순서가
가장 짧습니다.

1. `api.js`의 `request`·`refreshPromise`·`performRefresh`
2. `JwtAuthenticationFilter.shouldNotFilter`와 `SecurityConfig`의 refresh 경로 규칙
3. `SessionOriginInterceptor.preHandle`
4. `SessionController.refreshSession`
5. `SessionService.refreshSession`
6. `RefreshTokenProvider`의 생성·hash·만료 시각 method
7. `AuthSessionRepository.findByRefreshTokenHash`
8. `AuthSession.isActive`·`rotate`
9. `SessionRefreshResponseDto`와 `RefreshCookieProvider`
10. `GlobalExceptionHandler` 및 refresh 관련 테스트

`api.js`의 공통 `sendRequest`, JWT parser, `CustomUserDetails` 같은 개념은 05·06장에서
이미 확인했으므로 전체를 다시 복사하지 않습니다. 다만 Refresh 흐름에 직접 필요한
함수 body와 그 함수가 전달받고 반환하는 값은 이 장에서 생략하지 않습니다.

### 이 장의 코드 block 표기 규칙

- `코드 원문`은 해당 파일에서 이 흐름에 필요한 class·method를 현재 source 그대로 옮긴 것입니다.
- `코드 원문: ...`처럼 특정 method만 표시한 block은 파일 전체가 아니라 그 method의 실제
  source 발췌입니다. import, 다른 기능 method, 이미 05·06장에서 완성한 공통 method를
  생략한 경우에는 바로 아래 설명에 생략 범위와 확인할 장을 적습니다.
- 생략은 실제 코드를 존재하지 않는 것으로 처리하는 것이 아닙니다. 특히 `SessionService`의
  `createSession`·`deleteSession`, `RefreshCookieProvider`의 만료 method, `AuthSession.revoke`
  처럼 다른 실행 흐름에 속한 method는 이 장의 Refresh 흐름과 섞지 않고 해당 장에서 다시
  설명합니다.

---

## 1. 보호 요청의 401이 Refresh 시작점이다

### 실제 코드 위치

`/Users/miles/Documents/GitHub/KTB4_Miles_Week12_Front/src/pages/posts/PostDetailPage.jsx:28-48`

### 코드 원문: 상세 화면이 보호 API를 호출하는 위치

~~~~jsx
useEffect(() => {
    const controller = new AbortController();
    setPost(null);
    setComments([]);
    setLoadError(null);

    postApi.getPost(postId, { signal: controller.signal })
        .then((result) => {
            setPost(result);
            setComments(result.comments);
            setLoadError(null);
        })
        .catch((requestError) => {
            if (controller.signal.aborted || requestError?.name === "AbortError") {
                return;
            }
            setLoadError(requestError);
        });

    return () => {
        controller.abort();
    };
}, [postId, retryVersion]);
~~~~

### 코드 바로 아래 설명

- `useEffect`의 내부에서 `postApi.getPost`가 실행됩니다. 여기서 `postId`는 현재 URL의
  `/posts/:postId` route parameter에서 온 값이고, `retryVersion`이 바뀌면 같은 상세 조회를
  다시 시작합니다.
- 이 코드 자체는 Refresh를 직접 호출하지 않습니다. `getPost`가 내부에서 공통
  `request`를 부르고, `request`가 401을 받았을 때 Refresh 흐름으로 이동합니다.
- 성공하면 `result`를 `post` state와 `comments` state에 넣습니다. Refresh가 성공한 뒤의
  재시도도 성공하면 이 `.then`이 동일하게 실행됩니다.
- `AbortController`는 화면이 바뀌어 요청을 취소한 경우를 구분하기 위한 장치입니다. 이번
  장의 Refresh 동시성 처리와는 별개이며, 취소된 요청이 일반 오류 화면으로 바뀌지 않게 합니다.

### 호출되는 API module

`/Users/miles/Documents/GitHub/KTB4_Miles_Week12_Front/src/api/postApi.js:24-28`

~~~~js
async getPost(postId, { signal } = {}) {
    return normalizeDetailPost(await request(`/posts/${postId}`, { signal }));
},
~~~~

### 코드 바로 아래 설명

- `getPost`는 `postId`로 `/posts/{id}` endpoint 문자열을 만들고 `request`에 전달합니다.
- `signal`은 fetch 취소용 옵션입니다. Refresh와 관련된 값은 아니지만, `request`가 원래
  options 객체를 재시도할 때 함께 유지되는 값입니다.
- `await request(...)`의 반환값은 백엔드 상세 응답 JSON입니다. `normalizeDetailPost`는
  API 응답 형태를 화면에서 사용하는 post 형태로 바꿉니다.

---

## 2. `api.js`가 401을 만나면 Refresh를 한 번 실행하고 원래 요청을 재시도한다

### 실제 파일

`/Users/miles/Documents/GitHub/KTB4_Miles_Week12_Front/src/api/api.js`

`sendRequest`의 header 작성은 06장에서 설명했으므로, 이 장에서는 401과 Refresh에 직접
관여하는 현재 source의 선언·함수 body를 그대로 배치합니다.

### 2.1 Refresh하면 안 되는 요청 목록

~~~~js
const NO_REFRESH_REQUESTS = new Set([
    "POST /sessions",
    "POST /sessions/refresh",
    "DELETE /sessions",
    "POST /users",
]);

function isRefreshExcludedRequest(endpoint, options = {}) {
    const method = (options.method ?? "GET").toUpperCase();
    return NO_REFRESH_REQUESTS.has(`${method} ${endpoint}`);
}
~~~~

### 코드 바로 아래 설명

- `NO_REFRESH_REQUESTS`는 `Set` 자료 구조입니다. 문자열 조합이 목록에 있는지 빠르게
  확인하는 용도로 사용됩니다.
- `POST /sessions/refresh` 자체를 목록에 넣은 이유는 Refresh 요청이 401을 받아 다시
  Refresh를 호출하는 무한 재귀를 막기 위해서입니다.
- 로그인·회원가입·로그아웃도 Refresh를 전제로 하지 않습니다. 특히 로그인은 아직
  Refresh Cookie가 없을 수 있고, 로그아웃은 인증 실패를 다시 재발급으로 연결하면 안 됩니다.
- `options.method ?? "GET"`에서 `??`는 왼쪽 값이 `null` 또는 `undefined`일 때만 `"GET"`을
  사용합니다. 빈 문자열은 기본값으로 바뀌지 않습니다. 실제 HTTP method 비교를 위해
  `toUpperCase()`로 대문자화합니다.
- `${method} ${endpoint}`는 예를 들어 `POST /sessions/refresh`라는 하나의 비교 key를
  만듭니다. `Set.has`가 true이면 현재 request는 Refresh 재시도 대상에서 제외됩니다.

### 2.2 Refresh 요청을 실제로 보내는 `performRefresh`

~~~~js
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
~~~~

### 코드 바로 아래 설명

- `sendRequest`의 세 번째 인자에 `false`를 넣어 Access Token header를 만들지 않습니다.
  따라서 Refresh endpoint는 `Authorization: Bearer ...`가 없어도 호출됩니다.
- `sendRequest` 자체에는 `credentials: "include"`가 있습니다. 이 옵션 때문에 브라우저가
  현재 domain의 HttpOnly Refresh Cookie를 요청에 포함합니다. JavaScript가 Cookie 원문을
  읽어서 전달하는 것이 아닙니다.
- `response.ok`는 HTTP status가 200~299인지 나타내는 boolean입니다. 실패하면
  `createRequestError`가 response status와 body를 가진 Error를 만들고 이 함수가 throw합니다.
- `result.data?.accessToken`의 `?.`는 optional chaining입니다. `data`가 null/undefined여도
  바로 TypeError를 내지 않고 undefined를 반환하므로, accessToken이 없는 응답을 오류로
  처리할 수 있습니다.
- 성공하면 JSON body에서 `accessToken`만 꺼내 `localStorage`에 덮어씁니다. 새 Refresh
  Token은 response header의 `Set-Cookie`를 browser가 처리하므로 이 함수가 Cookie를 읽거나
  저장하지 않습니다.
- 반환되는 `result.data`는 `refreshAccessToken`의 결과가 되지만, 일반 `request`는 주로
  재발급 성공 여부와 저장된 Access Token에 관심이 있습니다.

### 2.3 동시에 발생한 401이 같은 재발급 Promise를 공유한다

~~~~js
let refreshPromise = null;

export function refreshAccessToken() {
    if (!refreshPromise) {
        refreshPromise = performRefresh().finally(() => {
            refreshPromise = null;
        });
    }

    return refreshPromise;
}
~~~~

### 코드 바로 아래 설명

- `refreshPromise`는 module이 처음 평가될 때 한 번 만들어지는 변수입니다. 컴포넌트 state도,
  서버 DB 값도 아니며 이 browser tab의 JavaScript module 메모리에 있습니다.
- 첫 번째 401이 오면 `refreshPromise`가 null이므로 `performRefresh()`를 실행하고 Promise를
  변수에 저장합니다.
- 두 번째·세 번째 401이 첫 번째 요청이 끝나기 전에 들어오면 `!refreshPromise`가 false가
  되어 새 fetch를 만들지 않고 **같은 Promise**를 반환합니다. 따라서 브라우저에서 동시
  Refresh 요청이 여러 개 생기는 것을 줄입니다.
- `.finally(() => { refreshPromise = null; })`는 성공과 실패 모두에서 실행됩니다. 한 번의
  재발급이 끝난 뒤 다음 401이 새로운 재발급을 시작할 수 있도록 공유 변수를 초기화합니다.
- 이것은 backend DB row lock과 다른 층의 동시성 제어입니다. 같은 tab의 네트워크 중복을
  줄이는 front-end 제어일 뿐이며, 여러 backend 인스턴스나 여러 browser tab 전체를 잠그지는
  않습니다.

### 2.4 401이면 Refresh 후 원래 request를 다시 보낸다

~~~~js
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

- 첫 `sendRequest`가 원래 endpoint를 호출합니다. `result`는 `{ response, data }` 형태이므로
  status와 JSON body를 함께 가지고 있습니다.
- status가 정확히 `401`이고 `isRefreshExcludedRequest`가 false일 때만 Refresh를 시도합니다.
  403, 404, 500은 이 조건에 들어오지 않으며 바로 아래 오류 처리로 이동합니다.
- `await refreshAccessToken()`이 끝나면 `authStorage`에는 새 Access Token이 저장된 상태입니다.
  그래서 같은 `options`로 `sendRequest`를 다시 호출해도 내부의
  `authStorage.getAccessToken()`이 새 값을 읽어 header를 새로 만듭니다.
- 재시도 결과가 성공하면 마지막 `return result.data`가 원래 호출자에게 응답 JSON을 전달합니다.
  `PostDetailPage`에서는 이 값이 `normalizeDetailPost`를 거쳐 화면 state가 됩니다.
- Refresh가 실패해 401 Error가 되면 `clearAuthentication`이 Access Token을 지우고
  공개 경로가 아니면 `/login`으로 이동시킵니다. Refresh 실패가 아닌 다른 오류는 Error를
  호출자에게 그대로 던집니다.
- 이 코드는 원래 request를 무한히 반복하지 않습니다. Refresh 후 `sendRequest`를 한 번만
  다시 호출하고, 재시도도 실패하면 아래 오류 분기로 종료합니다.

---

## 3. `/sessions/refresh`는 Access Token Filter를 건너뛰지만 Origin 검사는 받는다

Refresh 요청은 만료됐거나 없는 Access Token을 새로 발급하기 위한 요청입니다. 따라서
이 endpoint 자체에 유효한 Access Token을 요구하면 재발급이 불가능합니다. 현재 코드는
Access Token Filter와 authorization rule에서 이 endpoint를 별도로 공개하고, Cookie session
요청에 대한 Origin 검사는 유지합니다.

### 3.1 JWT Filter가 refresh path를 건너뛴다

### 실제 파일

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

- `shouldNotFilter`의 반환값이 true이면 `OncePerRequestFilter`가 `doFilterInternal`을
  호출하지 않고 다음 filter로 요청을 넘깁니다.
- `/sessions/refresh`는 정확히 `POST` method와 path가 모두 일치할 때만 제외됩니다.
  `GET /sessions/refresh`나 다른 path까지 자동으로 공개되는 것은 아닙니다.
- 이 method는 “Refresh Token이 유효하다”는 판단을 하지 않습니다. 단지 Access Token
  검증을 이 endpoint에서 먼저 요구하지 않도록 요청 진입점을 선택합니다.

### 3.2 Security authorization도 refresh endpoint를 permitAll로 둔다

### 실제 파일

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

- `permitAll()`은 Spring Security의 authorization 단계에서 인증 객체가 없어도 이 경로를
  통과시키는 규칙입니다. 이것이 Cookie의 유효성 검증을 생략한다는 뜻은 아닙니다.
- `.anyRequest().authenticated()`는 위에 명시되지 않은 경로에는 인증이 필요하다는 기본
  규칙입니다. 그래서 `/sessions/refresh`를 명시하지 않았다면 refresh 요청도 인증 부족으로
  막혔을 것입니다.
- `addFilterBefore`는 사용자 정의 `jwtAuthenticationFilter`를
  `UsernamePasswordAuthenticationFilter` 앞 위치에 등록하는 설정입니다. 하지만
  `shouldNotFilter`가 true인 refresh 요청에는 해당 사용자 Filter의 내부 검증이 실행되지
  않습니다.

### 3.3 Origin interceptor는 Cookie를 사용하는 세 session 요청에 적용된다

### 실제 파일

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

- MVC handler가 실행되기 전에 `preHandle`이 호출됩니다. `isCookieSessionRequest`가 false면
  이 Interceptor는 아무 검사 없이 true를 반환합니다.
- Refresh는 `POST /sessions/refresh`이므로 이 검사 대상입니다. `Origin` header를 읽어
  `CorsOriginProvider.isAllowed`에 전달합니다.
- 허용된 Origin이면 true를 반환해 Controller까지 계속 진행합니다. Origin이 없거나 허용
  목록과 다르면 `ErrorResponseWriter`가 403 JSON을 직접 쓰고 false를 반환합니다.
- false는 MVC handler 호출을 중단한다는 뜻입니다. 이 오류는 Controller 내부의
  `GlobalExceptionHandler`로 전파되는 예외가 아니라, Interceptor가 응답을 끝내는 경로입니다.
- `permitAll`과 Origin 허용은 서로 다른 검사입니다. refresh는 Spring Security 인증 없이
  진입할 수 있지만, 현재 프로젝트의 Cookie session 요청은 허용 Origin이어야 합니다.

---

## 4. Controller가 Cookie를 Service 입력으로 바꾼다

### 실제 파일

`/Users/miles/Documents/GitHub/KTB4_Miles_Week12_Back/src/main/java/kr/adapterz/springdatajpa/controller/SessionController.java:40-60`

### 코드 원문: refresh endpoint

~~~~java
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
~~~~

### 코드 바로 아래 설명

- `@PostMapping("/refresh")`는 class의 `@RequestMapping("/sessions")`와 합쳐져 실제 경로
  `POST /sessions/refresh`를 만듭니다.
- `@CookieValue`는 HTTP request의 Cookie header에서 지정한 이름의 값을 찾아 method
  parameter에 넣는 Spring MVC annotation입니다. `name`에는 Provider가 공개한
  `COOKIE_NAME` 상수, 현재 값인 `refreshToken`이 들어갑니다.
- `required = false`이므로 Cookie가 없을 때 MVC가 parameter binding 오류를 즉시 만들지
  않고 `refreshToken`에 `null`을 넣어 Service까지 전달합니다. Service가 null·blank를
  `Invalid_Refresh_Token`으로 처리하는 이유가 이 설정과 연결됩니다.
- `HttpServletResponse response`는 Spring MVC가 현재 HTTP 응답을 만들 때 사용하는 객체를
  method parameter로 주입한 것입니다. Controller가 새 Cookie를 response header에 직접
  추가하기 위해 사용합니다.
- `sessionService.refreshSession(refreshToken)`의 반환값은 새 Access Token과 새 원문
  Refresh Token을 담은 DTO입니다. Service가 DB 검증·rotation을 끝내기 전에는 아래
  `addHeader`가 실행되지 않습니다.
- `response.addHeader`는 반환 DTO의 JSON field를 바꾸지 않고 HTTP 응답 header에
  `Set-Cookie` 한 줄을 추가합니다. `.toString()`은 `ResponseCookie` 객체를 실제 header
  문자열로 직렬화하는 부분입니다.
- 마지막 `return refreshResponse`는 Spring MVC가 DTO를 JSON body로 직렬화하게 합니다.
  따라서 같은 refresh 응답에 Cookie는 header로, Access Token과 message는 JSON body로
  각각 전달됩니다.

---

## 5. Service가 원문 Cookie를 검증하고 같은 session row를 회전시킨다

### 실제 파일

`/Users/miles/Documents/GitHub/KTB4_Miles_Week12_Back/src/main/java/kr/adapterz/springdatajpa/service/SessionService.java:24-27,83-119`

### 코드 원문: Service 선언과 refresh method

~~~~java
@Service
@RequiredArgsConstructor
@Transactional
public class SessionService {

    private final AuthenticationManager authenticationManager;
    private final JwtProvider jwtProvider;
    private final RefreshTokenProvider refreshTokenProvider;
    private final AuthSessionRepository authSessionRepository;

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
}
~~~~

### 코드 바로 아래 설명: 선언부와 transaction

- `@Service`는 이 class를 Spring Bean으로 등록해 Controller가 주입받게 합니다.
- `@RequiredArgsConstructor`는 `final` field 네 개를 parameter로 받는 생성자를 Lombok이
  만들어 줍니다. Controller가 Service를 직접 `new`하지 않고 Spring이 의존성을 주입합니다.
- `@Transactional`은 이 class의 public method 호출을 transaction 경계 안에서 실행하도록
  합니다. Refresh에서 repository 조회와 `AuthSession.rotate`가 같은 transaction에 묶이는
  것이 row lock과 dirty checking을 이해할 때 중요합니다.
- `authenticationManager`는 로그인 `createSession`에서만 사용되고, refresh method에서는
  사용되지 않습니다. Refresh는 비밀번호를 다시 검증하는 흐름이 아니라 이미 발급된
  Refresh Token과 DB session을 검증하는 흐름입니다.

### 코드 바로 아래 설명: 입력 존재 여부와 hash 조회

- `refreshToken == null`은 Cookie 자체가 없는 경우이고, `isBlank()`는 빈 문자열이나
  공백만 있는 경우까지 포함합니다. 둘 다 유효한 token으로 처리하지 않습니다.
- 원문 token을 DB에 그대로 비교하지 않고 `hashRefreshToken`으로 SHA-256 hash를 만듭니다.
  DB에는 원문이 아니라 hash만 저장되어 있으므로, 같은 원문이 들어오면 같은 조회 key가
  만들어집니다.
- `findByRefreshTokenHash`가 `Optional<AuthSession>`을 반환합니다. `orElseThrow`는 값이
  있으면 `AuthSession`을 꺼내고, 없으면 lambda가 만드는 `AuthException`을 즉시 던집니다.
  이 지점에서 실패하면 이후 새 token 발급 코드는 실행되지 않습니다.

### 코드 바로 아래 설명: session·User 상태 검증

- `authSession.isActive(LocalDateTime.now())`는 `revokedAt == null`이고
  `refreshExpiresAt`이 현재 시각보다 뒤에 있을 때만 true입니다. 이미 로그아웃으로 폐기됐거나
  만료된 row는 새 token 발급에 사용하지 않습니다.
- `authSession.getUser().isDeleted()`와 `isSuspended()`는 token 자체가 아니라 연결된 현재
  User entity의 상태를 확인합니다. 사용자가 로그인 후 탈퇴·정지되었으면 아직 Cookie가
  남아 있어도 refresh를 허용하지 않습니다.
- 세 조건에서 모두 같은 raw code `Invalid_Refresh_Token`을 사용합니다. 외부 응답에서
  “row 없음·만료·폐기·탈퇴·정지”를 서로 다른 보안 정보로 노출하지 않기 위한 현재 코드의
  응답 형태입니다.

### 코드 바로 아래 설명: 새 token 생성과 rotation

- `createRefreshToken()`은 새로운 원문을 만듭니다. 기존 원문을 재사용하지 않습니다.
- 새 원문은 `hashRefreshToken`으로 다시 hash되어 DB에 들어갈 값이 됩니다. Controller가
  Cookie로 보낼 값은 원문 `newRefreshToken`, DB에 남길 값은 `newRefreshTokenHash`입니다.
- `authSession.rotate(...)`는 기존 managed entity의 hash와 만료 시각 field를 새 값으로
  변경합니다. 새 `AuthSession`을 하나 더 만드는 것이 아니며, 기존 row가 새 token을
  가리키도록 바뀝니다.
- `createExpirationTime()`은 설정된 Refresh 만료 duration을 현재 시각에 더해 새 row 값으로
  전달합니다. Cookie의 max-age와 DB의 `refreshExpiresAt`은 각각 Provider 설정에서 같은
  만료 정책을 사용하지만, 하나는 browser Cookie 속성이고 다른 하나는 DB 검증 기준입니다.

### 코드 바로 아래 설명: 새 Access Token과 반환값

- `authSession.getUser().getUserId()`는 JWT subject로 들어갈 사용자 ID입니다.
- `getAuthVersion()`은 현재 User의 인증 버전입니다. 비밀번호 변경·탈퇴 등으로 버전이
  증가한 경우 새 Access Token도 현재 버전을 담습니다.
- `new SessionRefreshResponseDto(accessToken, newRefreshToken)`은 두 원문을 DTO에 담습니다.
  그러나 Controller 응답에서 Refresh 원문은 `@JsonIgnore` 때문에 JSON으로 노출되지 않고,
  Cookie 생성 호출에만 사용됩니다.
- 중간의 hash 생성, repository 조회, 상태 검증, JWT 생성 중 `AuthException`이나 다른
  runtime 예외가 발생하면 method가 정상 return하지 않습니다. 그러면 Controller의
  `addHeader`와 JSON return도 실행되지 않고, 현재 class-level `@Transactional` 경계에서는
  정상 commit이 진행되지 않습니다. `GlobalExceptionHandler`가 처리할 수 있는
  `AuthException`은 401 JSON으로 바뀌고, 그 밖의 예외는 별도 handler/기본 오류 경로로 갑니다.

---

## 6. RefreshTokenProvider는 원문 발급·hash 저장값·만료 시각을 분리한다

### 실제 파일

`/Users/miles/Documents/GitHub/KTB4_Miles_Week12_Back/src/main/java/kr/adapterz/springdatajpa/auth/RefreshTokenProvider.java:15-53`

### 코드 원문

~~~~java
@Component
public class RefreshTokenProvider {

    private static final int TOKEN_BYTE_LENGTH = 32;

    private final SecureRandom secureRandom = new SecureRandom();
    private final Duration refreshExpiration;

    public RefreshTokenProvider(
            @Value("${jwt.refresh-expiration-millis}") long refreshExpirationMillis
    ) {
        this.refreshExpiration = Duration.ofMillis(refreshExpirationMillis);
    }

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

    public LocalDateTime createExpirationTime() {
        return LocalDateTime.now().plus(refreshExpiration);
    }
}
~~~~

### 코드 바로 아래 설명

- `@Component`는 Provider를 Spring Bean으로 등록합니다. `SessionService`와
  `RefreshCookieProvider`가 생성자 주입으로 같은 설정값 기반 Bean을 사용합니다.
- `TOKEN_BYTE_LENGTH = 32`는 random byte 배열의 길이입니다. `SecureRandom.nextBytes`가
  예측하기 어려운 값을 채우고, URL-safe Base64 문자열로 변환합니다.
- `@Value("${jwt.refresh-expiration-millis}")`는 application YAML의 property 값을
  생성자 parameter로 주입하는 Spring 문법입니다. `${...}` 안의 key 이름이 같아야 해당
  설정값을 읽습니다.
- `MessageDigest.getInstance("SHA-256")`은 입력 byte를 SHA-256 digest로 바꾸는 Java
  표준 API입니다. hash는 원문으로 되돌리는 용도가 아니라 같은 원문을 같은 DB 조회 key로
  재현하는 용도입니다.
- `HexFormat.of().formatHex(tokenHash)`는 32 byte SHA-256 결과를 64자리 소문자 hex
  문자열로 바꿉니다. `AuthSession.refreshTokenHash` column도 `length = 64`로 선언되어 있습니다.
- SHA-256 algorithm이 Java runtime에 없다는 `NoSuchAlgorithmException`은 현재 환경에서
  구현 불가한 구성 오류이므로 `IllegalStateException`으로 바뀝니다. 일반적으로 이 예외는
  잘못된 사용자 입력이 아니라 서버 실행 환경 문제입니다.

---

## 7. Repository 조회에서 비관적 write lock을 요청한다

### 실제 파일

`/Users/miles/Documents/GitHub/KTB4_Miles_Week12_Back/src/main/java/kr/adapterz/springdatajpa/repository/AuthSessionRepository.java:15-18`

### 코드 원문

~~~~java
public interface AuthSessionRepository extends JpaRepository<AuthSession, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<AuthSession> findByRefreshTokenHash(String refreshTokenHash);
}
~~~~

### 코드 바로 아래 설명

- `AuthSessionRepository`는 Spring Data JPA의 `JpaRepository<AuthSession, Long>`을
  상속합니다. 따라서 `findById`, `save` 같은 기본 method를 직접 구현하지 않아도 됩니다.
- `findByRefreshTokenHash`라는 method 이름은 field 이름을 기반으로 Spring Data가 조회
  query를 생성하게 합니다. 의미는 `refreshTokenHash` field가 parameter와 같은 row를 찾는
  것입니다.
- 반환값 `Optional<AuthSession>`은 결과가 0개일 수 있음을 method 타입에 표시합니다.
  Service가 `orElseThrow`로 “row 없음”을 명시적으로 처리하는 이유입니다.
- `@Lock(LockModeType.PESSIMISTIC_WRITE)`는 이 조회를 수행할 때 DB에 해당 row의 write
  lock을 요청하도록 JPA에 알립니다. 같은 row를 다른 transaction이 동시에 rotation하려고
  하면 DB transaction isolation과 dialect에 따라 대기하거나 충돌합니다.
- 이 annotation이 class 전체나 모든 repository query에 자동 적용되는 것은 아닙니다.
  현재는 `findByRefreshTokenHash` method에만 붙어 있습니다. `revokeAllActiveByUser`와
  `deleteAllExpiredAtOrBefore`는 별도의 bulk query이고 이 lock annotation을 사용하지 않습니다.
- lock은 `SessionService`의 `@Transactional` transaction 안에서 repository query가 실행될
  때 의미가 있습니다. transaction 없이 단순히 annotation 글자만 있다고 lock이 모든 요청에
  계속 남아 있는 것은 아닙니다.

---

## 8. AuthSession이 활성 여부와 rotation을 담당한다

### 실제 파일

`/Users/miles/Documents/GitHub/KTB4_Miles_Week12_Back/src/main/java/kr/adapterz/springdatajpa/entity/AuthSession.java:19-81`

### 코드 원문: 현재 Refresh에 직접 쓰이는 부분

~~~~java
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

    public boolean isActive(LocalDateTime now) {
        return revokedAt == null && refreshExpiresAt.isAfter(now);
    }

    public void rotate(
            String refreshTokenHash,
            LocalDateTime refreshExpiresAt
    ) {
        this.refreshTokenHash = refreshTokenHash;
        this.refreshExpiresAt = refreshExpiresAt;
    }

    public void revoke(LocalDateTime revokedAt) {
        if (this.revokedAt == null) {
            this.revokedAt = revokedAt;
        }
    }
}
~~~~

### 코드 바로 아래 설명

- `@Entity`와 `@Table`은 이 class가 `auth_sessions` table과 매핑된다는 뜻입니다.
  `refresh_token_hash`의 `unique = true`는 서로 다른 session row가 같은 hash를 가지지
  않도록 DB 제약을 둡니다.
- `@ManyToOne(fetch = FetchType.LAZY)`의 `user`는 session과 User의 연결입니다. Refresh에서
  `getUser().isDeleted()`를 호출할 때 필요하면 User row를 조회합니다.
- `isActive(now)`는 두 조건을 동시에 검사합니다. `revokedAt == null`은 logout 등으로
  폐기되지 않았다는 뜻이고, `refreshExpiresAt.isAfter(now)`는 만료 시각이 현재보다 뒤라는
  뜻입니다. 만료 시각과 `now`가 정확히 같아도 `isAfter`가 false이므로 활성으로 보지 않습니다.
- `rotate`는 원문 token이 아니라 hash와 만료 시각만 받습니다. 원문은 browser Cookie와
  Controller response에 필요하지만 DB entity에는 저장하지 않는 설계입니다.
- 생성자는 로그인 시 `SessionService.createSession`이 처음 저장할 때도 호출됩니다. 이 장의
  Refresh 경로에서는 이미 저장된 entity를 repository에서 받아 사용하므로 constructor를
  다시 호출해 새 row를 만들지 않습니다.
- `authSession`은 `findByRefreshTokenHash`로 조회된 managed entity입니다. Service가
  `rotate`로 field를 바꾸고 별도 `save`를 호출하지 않아도, transaction commit 시 JPA dirty
  checking이 변경된 field를 감지해 UPDATE SQL을 만들 수 있습니다.
- 이 dirty checking은 “method를 호출하자마자 DB가 즉시 UPDATE된다”는 뜻은 아닙니다.
  현재 transaction 안에서 entity가 변경되고, commit/flush 시점에 영속성 context와 DB 값이
  동기화됩니다. 그 후 Controller가 새 Cookie header를 포함한 응답을 보냅니다.
- `revoke`는 logout 문서에서 사용되는 다른 상태 변경 method입니다. Refresh method는
  `revoke`를 호출하지 않고, `isActive`로 이미 폐기된 row인지 확인한 뒤 `rotate`만 호출합니다.

---

## 9. 새 Refresh Token은 DB entity와 Cookie에서 서로 다른 형태로 사용된다

### 9.1 Refresh 응답 DTO

### 실제 파일

`/Users/miles/Documents/GitHub/KTB4_Miles_Week12_Back/src/main/java/kr/adapterz/springdatajpa/dto/user/SessionRefreshResponseDto.java:6-22`

### 코드 원문

~~~~java
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
~~~~

### 코드 바로 아래 설명

- Service는 새 Access Token과 새 원문 Refresh Token을 둘 다 DTO에 넣습니다. Controller는
  `getRefreshToken()`으로 원문을 Cookie builder에 전달해야 하므로 DTO 내부에는 field가 필요합니다.
- `@JsonIgnore`는 Jackson이 이 field를 JSON response body로 직렬화하지 않도록 하는
  annotation입니다. 따라서 body에는 `message`와 `accessToken`이 포함되고 `refreshToken`은
  포함되지 않습니다.
- `@NoArgsConstructor`는 Jackson 등의 역직렬화에 필요한 no-argument constructor를
  Lombok이 만듭니다. Refresh 응답을 생성하는 현재 Service는 두 인자 constructor를 사용합니다.

### 9.2 새 원문 token을 Set-Cookie로 만드는 Provider

### 실제 파일

`/Users/miles/Documents/GitHub/KTB4_Miles_Week12_Back/src/main/java/kr/adapterz/springdatajpa/auth/RefreshCookieProvider.java:9-35`

### 코드 원문

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

    public ResponseCookie createRefreshTokenCookie(String refreshToken) {
        return ResponseCookie.from(COOKIE_NAME, refreshToken)
                .httpOnly(true)
                .secure(secure)
                .sameSite("Strict")
                .path(cookiePath)
                .maxAge(refreshExpiration)
                .build();
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

- `COOKIE_NAME`은 Controller의 `@CookieValue`와 Cookie builder가 공유하는 이름입니다.
  현재 값은 `refreshToken`이므로 request와 response가 같은 Cookie를 가리킵니다.
- `ResponseCookie.from(COOKIE_NAME, refreshToken)`은 기존 서버 Cookie 객체를 수정하는
  것이 아니라 새 응답 Cookie 객체를 만듭니다. browser는 같은 이름·path의 `Set-Cookie`를
  받으면 기존 Cookie 값을 새 원문으로 교체합니다.
- `httpOnly(true)`는 JavaScript가 `document.cookie`로 원문을 읽지 못하게 합니다.
  `credentials: "include"`인 fetch에는 browser가 Cookie를 자동 포함하지만, `api.js`가
  원문을 직접 꺼내지는 못합니다.
- `secure(secure)`는 설정값에 따라 HTTPS에서만 보내게 할지 결정합니다. 현재 설정값이
  local/prod profile에 따라 다를 수 있으므로 이 method만 보고 항상 HTTPS라고 단정하지 않습니다.
- `sameSite("Strict")`는 다른 site에서 시작한 요청에 Cookie가 자동으로 붙는 범위를 제한합니다.
  이것은 `SessionOriginInterceptor`의 Origin 검사와 별개의 Cookie 정책입니다.
- `maxAge(refreshExpiration)`은 새 Cookie의 browser 수명을 설정합니다. DB entity의
  `refreshExpiresAt` 검사는 서버가 별도로 수행하므로, 한쪽만 만료되어도 Service의
  `isActive` 검사가 최종적으로 유효성을 판단합니다.
- 생성자는 YAML property 세 개를 받아 Cookie builder가 사용할 값을 초기화합니다. 이
  장의 refresh 성공에서는 `createRefreshTokenCookie`가 그 field를 사용하고,
  `createExpiredRefreshTokenCookie`는 08장의 logout·계정 변경 흐름에서 다시 사용됩니다.

---

## 10. 성공 응답은 transaction 변경·Cookie header·JSON body로 완성된다

~~~~text
SessionService.refreshSession
├─ AuthSession managed entity field 변경
├─ 새 Access Token·새 원문 Refresh Token DTO 생성
└─ return DTO
      ↓
SessionController.refreshSession
├─ DTO의 refreshToken으로 ResponseCookie 생성
├─ HttpServletResponse.addHeader("Set-Cookie", ...)
└─ return DTO
      ↓
Spring MVC
├─ response header: Set-Cookie: refreshToken=<new raw token>; ...
└─ response body: {"message":"refresh_success","accessToken":"..."}
~~~~

### 코드 바로 아래 설명

- Service가 return했다고 DB 변경과 Cookie 전송이 각각 사라지는 것은 아닙니다. Service의
  transaction은 method가 정상적으로 끝나고 commit되는 과정에서 managed entity 변경을
  DB에 flush합니다.
- Controller의 `response.addHeader`는 DTO에 field를 추가하는 연산이 아니라 현재 HTTP
  응답 객체에 header를 추가하는 연산입니다. 뒤의 `return`은 별도로 JSON body를 만들도록
  Spring MVC에 반환값을 제공합니다.
- browser는 `Set-Cookie` header를 받아 새 Refresh Token을 저장하고, JSON parser는
  `accessToken`을 프론트 코드가 읽을 수 있게 합니다. `HttpOnly`라서 Refresh 원문은
  JavaScript 코드가 직접 읽지 않습니다.
- 현재 Controller에는 `@ResponseStatus`가 refresh method에 붙어 있지 않으므로 성공 status는
  Spring MVC 기본 성공 응답인 200으로 처리됩니다. 이 문서는 실행 결과를 직접 돌려 증명한
  것이 아니라 annotation과 method 반환 구조에 근거한 해석입니다.

---

## 11. 실패한 Refresh는 GlobalExceptionHandler에서 401 JSON이 된다

### 실제 파일

`/Users/miles/Documents/GitHub/KTB4_Miles_Week12_Back/src/main/java/kr/adapterz/springdatajpa/controller/GlobalExceptionHandler.java:33-35,66-74`

### 코드 원문

~~~~java
@ExceptionHandler(AuthException.class)
public ResponseEntity<ErrorResponseDto> handleAuthException(AuthException e){
    return error(HttpStatus.UNAUTHORIZED, e.getMessage());
}

private ResponseEntity<ErrorResponseDto> error(HttpStatus status, String rawCode) {
    ApiErrorCode errorCode = ApiErrorCode.from(rawCode);
    ErrorResponseDto response = new ErrorResponseDto(
            errorCode.getCode(),
            errorCode.getMessage(),
            status.value()
    );
    return ResponseEntity.status(status).body(response);
}
~~~~

### 코드 바로 아래 설명

- `@RestControllerAdvice`가 붙은 `GlobalExceptionHandler`는 Controller 호출 중 전파된
  예외를 이 handler method와 연결합니다. `SessionService.refreshSession`이 던진
  `AuthException`이 이 경로로 올라오면 401 response가 만들어집니다.
- `AuthException`의 raw message는 현재 `Invalid_Refresh_Token`입니다. `ApiErrorCode.from`
  이 문자열을 외부 응답용 enum `INVALID_REFRESH_TOKEN`과 사용자 메시지로 정규화합니다.
- `ResponseEntity.status(status).body(response)`는 HTTP status와 JSON body를 함께 명시하는
  Spring MVC 반환 타입입니다. 이 경로에서는 Controller가 성공 때처럼 새 Cookie header를
  추가하지 못하고, Service 예외가 발생한 지점에서 정상 return도 실행되지 않습니다.
- 반대로 `SessionOriginInterceptor`가 Origin을 거부하는 경우에는 Controller까지 들어오지
  않으므로 이 `GlobalExceptionHandler`를 거치지 않고 `ErrorResponseWriter`가 직접 403을
  씁니다. 두 오류 경계를 섞으면 안 됩니다.

### raw code가 외부 응답 code로 바뀌는 실제 enum

`/Users/miles/Documents/GitHub/KTB4_Miles_Week12_Back/src/main/java/kr/adapterz/springdatajpa/exception/ApiErrorCode.java:18,51-64`

~~~~java
INVALID_REFRESH_TOKEN(
        "INVALID_REFRESH_TOKEN",
        "로그인 시간이 만료되었습니다. 다시 로그인해주세요."
),

public static ApiErrorCode from(String rawCode) {
    if (rawCode == null || rawCode.isBlank()) {
        return UNKNOWN_ERROR;
    }

    String normalized = rawCode.trim().toUpperCase().replace('-', '_');
    for (ApiErrorCode errorCode : values()) {
        if (errorCode.code.equals(normalized)) {
            return errorCode;
        }
    }

    return UNKNOWN_ERROR;
}
~~~~

### 코드 바로 아래 설명

- Service가 던지는 `Invalid_Refresh_Token`은 enum의 대문자 code와 표기가 다릅니다.
- `trim()`으로 양끝 공백을 제거하고 `toUpperCase()`로 대문자로 바꾼 뒤 `-`를 `_`로
  바꾸므로 `Invalid_Refresh_Token`은 `INVALID_REFRESH_TOKEN`으로 정규화됩니다.
- `values()`는 enum 상수 전체 배열을 반환하고, for-each가 code가 같은 상수를 찾습니다.
  찾으면 그 enum의 code와 사용자 메시지가 Error DTO에 사용됩니다.
- 빈 값이나 등록되지 않은 raw code는 `UNKNOWN_ERROR`가 됩니다. 현재 Refresh method는
  네 분기 모두 등록된 `Invalid_Refresh_Token`을 사용하므로 정상적인 실패 응답은
  `INVALID_REFRESH_TOKEN`을 목표로 합니다.

---

## 12. 같은 old Refresh Token의 동시 요청에서 lock이 하는 일과 하지 않는 일

### 현재 코드에서 확인되는 보호 장치

1. 같은 browser tab의 여러 401은 `refreshPromise`가 하나의 front-end refresh 요청으로 합칩니다.
2. 서로 다른 tab 또는 서로 다른 backend instance의 요청은 각자 refresh 요청을 보낼 수 있습니다.
3. backend의 `findByRefreshTokenHash`는 `PESSIMISTIC_WRITE` row lock을 요청합니다.
4. lock을 얻은 transaction이 `AuthSession.rotate`로 old hash를 new hash로 바꿉니다.
5. transaction commit 뒤 다음 transaction이 같은 old hash를 조회할 때의 결과는 DB isolation과
   timing에 영향을 받습니다. 현재 코드와 Mockito 단위 테스트만으로 “항상 특정 예외가 된다”
   고 단정할 수 없습니다.

### 시점 예시

~~~~text
요청 A: old hash 조회 → row lock 획득 → new hash로 rotate → commit
요청 B: 같은 old hash 조회를 시도 → A의 lock이 풀릴 때까지 대기할 수 있음
       → 이후 DB가 보이는 값과 transaction isolation에 따라 row 없음/실패 경로
~~~~

### 반드시 구분할 점

- `refreshPromise`는 module 변수이므로 여러 browser tab, 여러 서버 process, 여러 backend
  instance 사이의 분산 lock이 아닙니다.
- `PESSIMISTIC_WRITE`는 DB row를 보호하는 mechanism이지, 전체 refresh endpoint를 하나씩만
  실행하게 하는 전역 lock은 아닙니다.
- 현재 `SessionServiceTest`는 Mockito로 repository 반환값과 entity field 변경을 확인합니다.
  실제 두 transaction이 동시에 같은 DB row를 잡는지, 어떤 DB error가 반환되는지는 이 단위
  테스트가 증명하지 않습니다.
- 따라서 문서에서 “동시 요청은 항상 `Already_Reported` 같은 하나의 예외로 변환된다”라는
  식으로 쓰면 안 됩니다. Refresh의 현재 source가 보장하는 범위는 row lock을 요청하고,
  성공 transaction에서 rotation을 수행한다는 데까지입니다.

---

## 13. Refresh 관련 테스트가 확인하는 범위

### 13.1 Service 단위 테스트

`/Users/miles/Documents/GitHub/KTB4_Miles_Week12_Back/src/test/java/kr/adapterz/springdatajpa/service/SessionServiceTest.java:143-273`

현재 확인된 method입니다.

- `리프레시_토큰이_유효하면_새_액세스_토큰과_새_리프레시_토큰을_발급하고_세션을_회전한다`
  - hash 조회 결과로 받은 `AuthSession`을 사용합니다.
  - 새 Access Token·새 Refresh Token이 응답에 들어가는지 확인합니다.
  - entity의 hash와 만료 시각이 새 값으로 바뀌는지 확인합니다.
- `리프레시_토큰이_없으면_Invalid_Refresh_Token_예외가_발생한다`
  - blank input에서 repository와 JWT provider가 불필요하게 호출되지 않는지 확인합니다.
- `저장된_세션이_없으면_Invalid_Refresh_Token_예외가_발생한다`
  - hash row가 없을 때 AuthException을 확인합니다.
- `폐기된_세션이면_Invalid_Refresh_Token_예외가_발생한다`
  - `revoke`된 entity가 거부되는지 확인합니다.
- `만료된_세션이면_Invalid_Refresh_Token_예외가_발생한다`
  - 과거 만료 시각 entity가 거부되는지 확인합니다.
- `탈퇴한_유저의_세션이면_Invalid_Refresh_Token_예외가_발생한다`
  - session은 살아 있어도 User가 삭제되면 거부되는지 확인합니다.

이 테스트는 `@ExtendWith(MockitoExtension.class)`와 mock repository/provider를 사용합니다.
그러므로 실제 `@Transactional`, JPA dirty checking, DB row lock, Cookie browser 저장까지
검증하는 통합 테스트는 아닙니다.

### 13.2 Provider·Entity 단위 테스트

`/Users/miles/Documents/GitHub/KTB4_Miles_Week12_Back/src/test/java/kr/adapterz/springdatajpa/auth/RefreshTokenProviderTest.java`

- random Refresh Token이 43자리 URL-safe Base64이고 `=` padding이 없는지 확인합니다.
- 같은 원문이 같은 SHA-256 hex hash가 되고 다른 원문과 달라지는지 확인합니다.
- 설정한 3시간 뒤 범위 안에 만료 시각이 생성되는지 확인합니다.

`/Users/miles/Documents/GitHub/KTB4_Miles_Week12_Back/src/test/java/kr/adapterz/springdatajpa/entity/AuthSessionTest.java`

- 생성자의 user/hash/expiration/createdAt/revokedAt 기본값을 확인합니다.
- revoke되지 않고 만료되지 않은 경우만 `isActive`가 true인지 확인합니다.
- `rotate`가 hash와 만료 시각을 교체하는지 확인합니다.
- `revoke`를 두 번 호출해도 최초 시각만 유지하는지 확인합니다.

### 13.3 Controller 위임 테스트

`/Users/miles/Documents/GitHub/KTB4_Miles_Week12_Back/src/test/java/kr/adapterz/springdatajpa/controller/ControllerDelegationTest.java:136-180`

`SessionController는_refresh_Cookie를_발급하고_만료시킨다` 테스트는 다음을 확인합니다.

- refresh Controller가 Service에 old token을 전달하는지
- Service의 refresh response에서 새 token을 꺼내 Provider에 전달하는지
- `HttpHeaders.SET_COOKIE` header가 추가되는지
- delete 흐름에서는 만료 Cookie Provider를 호출하는지

이는 Spring MVC가 실제 annotation을 해석하는 통합 테스트가 아니라 Controller method를
직접 호출하는 Mockito delegation test입니다.

### 13.4 현재 테스트에서 확인되지 않는 범위

- 실제 DB에서 두 transaction이 같은 `PESSIMISTIC_WRITE` row lock을 경쟁하는 refresh concurrency test
- 실제 browser가 `HttpOnly` Cookie를 저장·교체하는 동작
- frontend `refreshPromise`가 여러 컴포넌트의 동시 401을 하나로 합치는 자동화 test
- `POST /sessions/refresh`에 Origin 없음·불허 Origin을 모두 직접 검증하는 `SecurityConfigTest`

위 항목은 현재 source/test 파일에서 확인되지 않았습니다. 실행하지 않은 테스트를 통과했다고
표현하지 않습니다.

---

## 14. 이 장에서 새로 등장한 문법과 이미 본 문법

### 새로 또는 이 흐름에서 다시 확인해야 하는 문법

- Java `Optional.orElseThrow`: Optional에 값이 없을 때 지정한 예외를 던지고, 값이 있으면 꺼냅니다.
- JPA `@Lock(LockModeType.PESSIMISTIC_WRITE)`: repository query에 DB row write lock을 요청합니다.
- JPA managed entity dirty checking: transaction 안에서 관리 중인 entity field 변경을 commit/flush 시 DB update로 반영합니다.
- Spring MVC `@CookieValue(required = false)`: Cookie header 값을 method parameter로 받고, 없으면 null을 허용합니다.
- Jackson `@JsonIgnore`: DTO field를 JSON 직렬화 대상에서 제외합니다.
- JavaScript `Set`, `??`, optional chaining `?.`, Promise `.finally`: Refresh 제외 목록, 기본 method, 응답 field 안전 접근, 성공·실패 공통 정리를 구현합니다.

### 앞 장에서 이미 설명한 문법

- `@RestController`, `@RequestMapping`, `@PostMapping`, 생성자 주입, `@Transactional`
- `fetch`, `credentials: "include"`, `Authorization: Bearer`, `async/await`, `useEffect`
- JWT 발급·검증, `SecurityContext`, `permitAll`, `addFilterBefore`
- `ResponseCookie`, `HttpServletResponse.addHeader`, `ResponseEntity`

반복 문법도 이 장의 Refresh 흐름에서 어떤 값과 연결되는지는 코드 바로 아래에서 다시
확인했습니다. “앞에서 설명했다”는 이유로 `refreshPromise`, Cookie 값, hash 값,
rotation 반환값을 생략하지 않았습니다.

---

## 15. 다음에 읽을 파일과 스킵 범위

### 다음 학습 시작점

다음은 `08_인증_로그아웃_계정변경_Cookie_Origin_CORS.md`입니다. Refresh와 달리 로그아웃은
새 token을 발급하지 않고, DB session을 revoke한 뒤 만료 Cookie를 내려 browser 상태를
무효화하는 흐름입니다. 같은 `SessionController`, `SessionService`, `RefreshCookieProvider`가
다시 나오지만 method와 상태 변경이 다르므로 해당 부분은 다시 설명해야 합니다.

### 이 장에서 반복하지 않은 파일

- `JwtProvider.java`: 새 Access Token을 만드는 호출 위치와 입력은 설명했지만, JWT claim·signature
  parser 전체 원문은 06장에 있습니다. 이 장에서 다시 전체를 읽지 않아도 Refresh 반환값의
  Access Token 출처를 확인할 수 있기 때문입니다.
- `CustomUserDetails.java`: `getUserId`, `getAuthVersion` 반환값은 Service 호출에 연결했지만,
  Spring Security principal 구현 전체는 06장에 있습니다.
- `api.js`의 `sendRequest`, `readResponseData`, `createRequestError`, `clearAuthentication`:
  Refresh에 필요한 호출 결과와 오류 연결은 설명했지만, 공통 구현 전체는 05·06장에서 확인합니다.

### 현재 흐름에서 사용되지 않는 확인

`authApi.refresh()`는 `/Users/miles/Documents/GitHub/KTB4_Miles_Week12_Front/src/api/authApi.js`
에 선언되어 있지만 현재 source 검색에서 호출자를 확인하지 못했습니다. 실제 401 흐름은
`api.js.request`가 `refreshAccessToken()`을 직접 호출합니다. 따라서 `authApi.refresh()`를
사용자가 Refresh를 시작하는 현재 실행 경로라고 설명하면 안 됩니다.

## 진행 상태

- 공식 파일 진행도: **52/213 (약 24.4%)**
- 이번 문서에서 새로 추가 집계한 파일: **0개**. Refresh 관련 파일은 04~06장 static source 대조에서 이미 공식 집계했으며, 이 장에서는 그 파일들을 Refresh 실행 순서로 재배치해 연결했습니다.
- 이번 문서에서 확인한 핵심 파일: `api.js`, `postApi.js`, `PostDetailPage.jsx`, `JwtAuthenticationFilter.java`, `SecurityConfig.java`, `SessionOriginInterceptor.java`, `SessionController.java`, `SessionService.java`, `RefreshTokenProvider.java`, `AuthSessionRepository.java`, `AuthSession.java`, `SessionRefreshResponseDto.java`, `RefreshCookieProvider.java`, `GlobalExceptionHandler.java`, `SessionServiceTest.java`, `RefreshTokenProviderTest.java`, `AuthSessionTest.java`, `ControllerDelegationTest.java`
- 문서 작성 상태: **완료**
- 사용자 이해 checkpoint: `401 → refreshPromise → Cookie → hash 조회/row lock → rotation → Set-Cookie·JSON → 원래 요청 재시도` 연결 확인 대기
- 다음 학습 시작점: `08_인증_로그아웃_계정변경_Cookie_Origin_CORS.md`의 `SessionController.deleteSession`
- 실행하지 않은 검증: backend/frontend 테스트, 실제 Spring context, 실제 DB transaction/lock, browser Cookie, Redis, Docker, 배포
