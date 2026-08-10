# 06장. Access Token 보호 요청 전체 흐름

05장에서 로그인에 성공하면 백엔드는 Access Token 문자열을 JSON body로 반환하고,
프론트는 그 값을 `localStorage`에 저장합니다. 이 장에서는 그 다음 요청부터 시작합니다.
브라우저가 저장된 문자열을 `Authorization: Bearer ...` header에 넣고, 백엔드가 서명·만료·사용자
상태·`authVersion`을 확인한 뒤 현재 요청의 `SecurityContext`에 인증 결과를 만들고,
`@AuthenticationPrincipal`을 통해 보호 Controller까지 전달하는 과정을 추적합니다.

> 이 문서는 계획에 따라 새로 만든 Access Token 기준 문서입니다. 기존
> `05_인증_AccessToken_요청인증.md`는 비교용으로 보존하고, 기존
> `06_인증_RefreshToken_재발급과_세션Rotation.md`는 새 07장으로 옮길 때까지 덮어쓰지 않습니다.

## 이 장에서 완성할 실행 지도

~~~~text
05장 로그인 성공
→ JSON body의 accessToken
→ authStorage.setAccessToken
→ localStorage("accessToken")

보호 화면 또는 기능에서 API 호출
→ postApi.getPost / postApi.getPosts 등
→ api.request
→ sendRequest가 authStorage.getAccessToken 호출
→ Authorization: Bearer <accessToken> header 생성
→ fetch
→ SecurityFilterChain
→ JwtAuthenticationFilter.shouldNotFilter=false
→ JwtAuthenticationFilter.doFilterInternal
→ Bearer 형식 확인
→ JwtProvider.getAccessTokenClaims
→ 서명·만료·subject·authVersion claim 검증
→ CustomUserDetailsService.loadUserByUserId
→ UserRepository.findByUserIdAndDeletedFalse
→ User deleted/suspended/authVersion 확인
→ UsernamePasswordAuthenticationToken 생성
→ SecurityContextHolder에 Authentication 저장
→ filterChain.doFilter
→ anyRequest().authenticated() 통과
→ @AuthenticationPrincipal CustomUserDetails 주입
→ 보호 Controller
→ Service가 userDetails.getUserId() 사용
→ JSON response
~~~~

이 흐름에서 반드시 분리할 세 가지가 있습니다.

1. **프론트 route guard**는 localStorage에 문자열이 있는지만 확인합니다. JWT가 유효하다는 증명이 아닙니다.
2. **JWT parser**는 token이 비밀키로 서명되었고 만료되지 않았는지 확인합니다. 이것만으로 현재 DB User가 유효하다고 확정하지 않습니다.
3. **Filter가 만든 Authentication**이 `SecurityContext`에 저장되어야 Security 인가와 Controller의 `@AuthenticationPrincipal`이 현재 요청을 인증된 것으로 사용할 수 있습니다.

## 실행 순서와 코드 읽기 순서

### 실제 실행 순서

1. React route가 보호 페이지를 렌더링합니다. route guard는 localStorage 값을 확인할 뿐입니다.
2. 페이지의 API module이 공통 `request`를 호출합니다.
3. `sendRequest`가 매 요청마다 최신 Access Token을 읽고 `Authorization` header를 붙입니다.
4. 요청이 Spring Security Filter chain에 들어갑니다.
5. 공개 경로가 아니므로 `shouldNotFilter`는 false이고 `doFilterInternal`이 실행됩니다.
6. Filter가 header에서 `Bearer ` 뒤의 token을 꺼내 `JwtProvider`로 검증합니다.
7. token subject의 User ID로 DB User를 다시 조회하고, 삭제·정지·`authVersion`을 확인합니다.
8. 검증된 `CustomUserDetails`로 `Authentication`을 만든 후 현재 `SecurityContext`에 저장합니다.
9. `filterChain.doFilter`가 다음 Filter와 DispatcherServlet으로 요청을 넘깁니다.
10. Security 인가 규칙 `anyRequest().authenticated()`가 context의 인증을 확인합니다.
11. Controller의 `@AuthenticationPrincipal CustomUserDetails` parameter에 같은 principal이 주입됩니다.
12. Controller가 `getUserId()`를 Service에 전달하고 JSON을 반환합니다.

### 이 장에서의 코드 읽기 순서

1. 05장에서 이미 만든 `authStorage`의 저장·조회
2. `ProtectedRoute`와 `AppRoutes`의 화면 접근 검사
3. `api.js`의 `sendRequest` header 생성
4. `postApi`·`PostDetailPage`의 보호 API 호출 시작점
5. `AccessTokenClaims` record
6. `JwtProvider` constructor·발급·검증
7. 새 04장에서 등록한 `SecurityFilterChain`의 Filter 위치·인가 규칙
8. `JwtAuthenticationFilter` 전체
9. `CustomUserDetailsService.loadUserByUserId`
10. `CustomUserDetails`와 `User`의 상태·`authVersion`
11. `PostController.getPostView`의 `@AuthenticationPrincipal`
12. 정상·실패 분기와 관련 테스트

앞에서 읽은 05장의 전체 `api.js`와 `CustomUserDetails`는 중복해서 다른 동작이라고 설명하지
않습니다. 이 장에서는 같은 코드가 보호 요청에서 어떤 입력과 반환을 받는지 다시 연결합니다.

---

## 1. route guard는 화면 접근을 막고, 서버 Filter는 API 인증을 한다

### 1.1 `authStorage`의 Access Token 저장·조회

### 실제 파일

`/Users/miles/Documents/GitHub/KTB4_Miles_Week12_Front/src/auth/authStorage.js`

### 코드 원문

~~~~js
const ACCESS_TOKEN_KEY = "accessToken";

export const authStorage = {
    getAccessToken() {
        return localStorage.getItem(ACCESS_TOKEN_KEY);
    },

    setAccessToken(accessToken) {
        localStorage.setItem(ACCESS_TOKEN_KEY, accessToken);
    },

    removeAccessToken() {
        localStorage.removeItem(ACCESS_TOKEN_KEY);
    },

    isLoggedIn() {
        return Boolean(authStorage.getAccessToken());
    },
};
~~~~

### 코드 바로 아래 설명

- 05장 로그인 성공 후 `setAccessToken(result.accessToken)`이 이 객체를 호출합니다. `localStorage.setItem`이 브라우저의 `accessToken` key에 JWT 문자열을 저장합니다.
- `getAccessToken()`은 저장된 문자열을 반환하거나 값이 없으면 `null`을 반환합니다. `api.js.sendRequest`가 요청할 때마다 이 method를 다시 호출하므로, 이전 요청과 같은 token을 영구적으로 캡처하지 않습니다.
- `isLoggedIn()`은 `Boolean(...)`으로 문자열 존재 여부만 true/false로 바꿉니다. 서명, 만료, DB User, `authVersion`은 검사하지 않습니다.
- `removeAccessToken()`은 `api.js.clearAuthentication`이 재발급도 실패했을 때 호출합니다. 이것은 Access Token만 지우며, backend의 Refresh Session이나 Cookie를 직접 지우지 않습니다.

### 1.2 `ProtectedRoute`는 API 호출 전에 화면 이동을 결정한다

### 실제 파일

`/Users/miles/Documents/GitHub/KTB4_Miles_Week12_Front/src/routes/ProtectedRoute.jsx`

### 코드 원문

~~~~jsx
import { Navigate, Outlet } from "react-router-dom";

import { authStorage } from "../auth/authStorage.js";

function ProtectedRoute() {
    if (!authStorage.isLoggedIn()) {
        return <Navigate to="/login" replace />;
    }

    return <Outlet />;
}

export default ProtectedRoute;
~~~~

### 코드 바로 아래 설명

- `ProtectedRoute`는 `AppRoutes`의 보호 `<Route element={<ProtectedRoute />}>` 아래에 있는 자식 route를 감싸는 component입니다.
- `authStorage.isLoggedIn()`이 false이면 `<Navigate>` element를 반환해 React Router가 `/login`으로 이동합니다. `replace`는 history에 현재 보호 경로를 남기지 않는 옵션입니다.
- true이면 `<Outlet />`을 반환합니다. `Outlet`은 부모 route 아래에서 실제로 매칭된 `/posts`, `/posts/:postId` 같은 자식 route element가 렌더링될 위치입니다. 이 코드가 API 인증을 완료했다는 뜻은 아닙니다.
- localStorage에 만료된 문자열이 남아 있어도 이 guard는 통과할 수 있습니다. 실제 보호 API 요청에서 backend Filter가 token을 검증하고, 실패하면 401을 반환합니다.

### 1.3 `AppRoutes`가 보호 화면과 PageBoundary를 연결한다

### 실제 파일과 관련 부분

`/Users/miles/Documents/GitHub/KTB4_Miles_Week12_Front/src/routes/AppRoutes.jsx`

import와 public route·wildcard route는 보호 route의 parent-child 관계에 필요하지 않아
생략한 실제 source 발췌입니다. 전체 route 구성은 12장과 현재 파일에서 확인합니다.

~~~~jsx
function page(element) {
    return <PageBoundary>{element}</PageBoundary>;
}

function AppRoutes() {
    return (
        <Routes>
            <Route element={<ProtectedRoute />}>
                <Route path="/posts" element={page(<PostsPage />)} />
                <Route path="/posts/:postId" element={page(<PostDetailPage />)} />
                <Route path="/posts/new" element={page(<PostCreatePage />)} />
                <Route path="/posts/:postId/edit" element={page(<PostEditPage />)} />
                <Route path="/profile/edit" element={page(<ProfileEditPage />)} />
                <Route path="/password/edit" element={page(<PasswordEditPage />)} />
            </Route>
        </Routes>
    );
}
~~~~

### 코드 바로 아래 설명

- 이 route 설정은 화면 접근의 부모 관계를 정합니다. `/posts/:postId`를 방문하면 먼저 `ProtectedRoute`가 실행되고, 통과한 뒤 `PageBoundary` 안에서 `PostDetailPage`가 렌더링됩니다.
- `page(element)`은 사용자 정의 함수입니다. React Router가 자동으로 `PageBoundary`를 삽입하는 문법이 아니라, 이 프로젝트가 route element를 `PageBoundary`로 감싸기 위해 직접 만든 함수입니다.
- `lazy`, `Suspense`, `PageBoundary`의 페이지 로딩·오류 처리는 프론트 실행 문서에서 이미 다룬 공통 개념입니다. 이 장에서는 route guard 통과가 backend JWT 인증과 별개라는 연결만 확인합니다.

---

## 2. `api.js`가 매 보호 요청에 최신 token을 header로 넣는다

### 실제 파일

`/Users/miles/Documents/GitHub/KTB4_Miles_Week12_Front/src/api/api.js`

05장에서 이 파일 전체를 이미 확인했으므로, 이 장에서는 보호 요청에 직접 필요한 실제
함수 body를 발췌합니다. 아래는 임의 축약이 아니라 현재 source의 해당 구간입니다.

### 코드 원문: `sendRequest`

~~~~js
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
~~~~

### 코드 바로 아래 설명

- `sendRequest`는 `api.js.request`가 호출하는 내부 function입니다. 보호 요청에서 `includeAccessToken` 기본값은 true입니다.
- `authStorage.getAccessToken()`이 현재 localStorage 값을 읽습니다. 05장 로그인 직후 저장한 token, 이후 Refresh로 교체된 token 중 그 시점의 최신 값이 들어옵니다.
- `headers.Authorization = \`Bearer ${accessToken}\``은 문자열 앞에 정확히 `Bearer `를 붙입니다. backend Filter의 `startsWith("Bearer ")` 조건과 한 쌍으로 동작합니다.
- `includeAccessToken`이 false인 호출은 `performRefresh`의 `/sessions/refresh` 요청입니다. 이 장의 보호 API 요청은 기본 true이므로 header가 추가됩니다.
- `fetch`의 반환 `Response`와 `readResponseData` 결과를 `{ response, data }` 객체로 묶어 반환합니다. `request`가 status를 보고 refresh/오류 분기를 수행합니다.

### 코드 원문: `request`의 401 재시도와 오류 경계

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

- 보호 요청이 처음 401을 받으면 `request`는 `NO_REFRESH_REQUESTS`에 포함되지 않은 endpoint인지 확인한 후 `refreshAccessToken()`을 시도합니다. 실제 Refresh 실행과 동시 요청 공유는 새 07장의 주제입니다.
- 이 장에서 중요한 점은 첫 요청에 Access Token을 붙이는 책임과, token이 거부된 뒤 재발급할지 결정하는 책임이 모두 frontend `api.js`에 있다는 것입니다. backend Filter가 frontend 재시도를 호출하지 않습니다.
- Refresh가 성공하면 `sendRequest(endpoint, options)`를 다시 호출합니다. 이 재호출도 함수 내부에서 `authStorage.getAccessToken()`을 다시 읽으므로 새 token을 header에 넣습니다.
- 재시도 후에도 `response.ok`가 false이거나 Refresh 자체가 401이면 `clearAuthentication`이 실행될 수 있고 `createRequestError`가 Error를 만들어 caller로 reject합니다.
- 이 장에서는 Refresh의 Cookie·hash·rotation을 설명하지 않습니다. 여기서는 Access Token이 없거나 만료되었을 때 frontend가 다음 07장으로 연결되는 경계만 확인합니다.

### 2.1 보호 API 호출 시작점: 게시글 상세

### 실제 파일

- `/Users/miles/Documents/GitHub/KTB4_Miles_Week12_Front/src/pages/posts/PostDetailPage.jsx`
- `/Users/miles/Documents/GitHub/KTB4_Miles_Week12_Front/src/api/postApi.js`

~~~~jsx
// PostDetailPage.jsx
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

~~~~js
// postApi.js
async getPost(postId, { signal } = {}) {
    return normalizeDetailPost(await request(`/posts/${postId}`, { signal }));
},
~~~~

### 코드 바로 아래 설명

- `PostDetailPage`의 `useEffect`는 route parameter `postId`가 바뀌거나 retry version이 바뀔 때 상세 API를 호출합니다. `AbortController`는 페이지가 unmount되거나 다른 post로 바뀔 때 이전 fetch를 취소합니다.
- `postApi.getPost`는 `/posts/${postId}` endpoint와 `signal`을 공통 `request`에 전달합니다. 이 function은 Authorization header를 직접 만들지 않고 `api.js`에 위임합니다.
- `await`가 받은 result는 `normalizeDetailPost`를 거쳐 화면 state로 들어갑니다. 이 normalize 과정은 Access Token 인증을 수행하지 않습니다.
- 이 호출의 runtime 경로는 `PostDetailPage` → `postApi.getPost` → `request` → `sendRequest` → `fetch`입니다. 그 다음부터 backend `JwtAuthenticationFilter`가 실행됩니다.
- `AbortError`는 인증 실패가 아니라 요청 취소입니다. 화면 state를 오류로 바꾸지 않고 return하는 분기입니다.

### `PostsPage` 목록도 같은 인증 header를 사용한다

`PostsPage` → `postApi.getPosts` → `request`라는 호출 구조도 같습니다. 목록·상세·작성·수정·댓글 API의 개별 endpoint가 달라도 `api.js.sendRequest`가 공통 header를 만들기 때문에 JWT 검증 Filter는 같은 방식으로 실행됩니다. 이 장에서는 목록 페이징·IntersectionObserver 문법을 반복하지 않고, 인증 header가 공통 module에서 생성된다는 사실만 사용합니다.

---

## 3. `AccessTokenClaims`는 JWT 검증 결과를 Filter에 전달한다

### 실제 파일

`/Users/miles/Documents/GitHub/KTB4_Miles_Week12_Back/src/main/java/kr/adapterz/springdatajpa/auth/AccessTokenClaims.java`

### 코드 원문

~~~~java
package kr.adapterz.springdatajpa.auth;

public record AccessTokenClaims(
        Long userId,
        long authVersion
) {
}
~~~~

### 코드 바로 아래 설명

- `record`는 값을 보관하는 불변 데이터 carrier를 짧게 선언하는 Java 문법입니다. 이 선언으로 canonical constructor와 `userId()`, `authVersion()` accessor가 생성됩니다. `getUserId()` 형태가 아니라 record component 이름 뒤에 괄호를 붙입니다.
- `AccessTokenClaims`는 인증을 수행하거나 DB를 조회하지 않습니다. `JwtProvider.getAccessTokenClaims`가 검증한 JWT payload를 두 값으로 묶어 Filter에 반환하는 전용 결과 객체입니다.
- `userId`는 발급할 때 JWT subject에 넣었던 문자열을 `Long`으로 변환한 값입니다. `authVersion`은 password 변경 등으로 token을 무효화할 때 비교할 custom claim입니다.
- Filter의 `tokenClaims.userId()`가 `CustomUserDetailsService.loadUserByUserId`의 입력이 되고, `tokenClaims.authVersion()`이 현재 User의 version과 비교됩니다.

---

## 4. `JwtProvider`가 같은 SecretKey로 발급·검증한다

### 실제 파일

`/Users/miles/Documents/GitHub/KTB4_Miles_Week12_Back/src/main/java/kr/adapterz/springdatajpa/auth/JwtProvider.java`

### 코드 원문

~~~~java
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

@Component
public class JwtProvider {
    private static final String AUTH_VERSION_CLAIM = "authVersion";

    private final SecretKey secretKey;
    private final long accessExpirationMillis;

    public JwtProvider(
            @Value("${jwt.secret}") String secret,
            @Value("${jwt.access-expiration-millis}") long accessExpirationMillis
    ) {
        this.secretKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.accessExpirationMillis = accessExpirationMillis;
    }

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
~~~~

### 코드 바로 아래 설명: Bean 초기화와 key 공유

- `@Component`가 애플리케이션 시작 때 `JwtProvider`를 하나의 Spring Bean으로 등록합니다. 요청마다 provider와 key를 새로 만드는 구조가 아닙니다.
- `@Value("${jwt.secret}")`는 YAML의 `jwt.secret` property를 constructor parameter `secret`에 주입하라는 뜻입니다. Java parameter 이름이 YAML key와 같아서 연결되는 것은 아닙니다.
- `secret.getBytes(StandardCharsets.UTF_8)`가 문자열을 byte 배열로 바꾸고, `Keys.hmacShaKeyFor(...)`가 JJWT가 사용할 `SecretKey` 객체를 만듭니다. 이 field는 constructor가 한 번 초기화하고 `createAccessToken`과 `getAccessTokenClaims`가 공유합니다.
- 발급과 검증에 같은 key를 쓰는 이유는 HMAC signature를 검증할 때 발급 시 서명에 사용한 비밀값이 필요하기 때문입니다. 다른 key로 서명된 token은 parser에서 실패합니다.
- `accessExpirationMillis`는 YAML의 만료 시간입니다. `createAccessToken`은 현재 시각에 이 값을 더해 expiration claim을 저장합니다.

### 코드 바로 아래 설명: Access Token 발급

- `subject(String.valueOf(userId))`는 User ID를 JWT의 subject claim에 문자열로 저장합니다. `claims.getSubject()`가 ID가 되는 이유는 발급 코드가 그 위치에 ID를 넣었기 때문입니다.
- `claim(AUTH_VERSION_CLAIM, authVersion)`은 두 인자를 받습니다. 첫 번째는 claim key인 `"authVersion"`, 두 번째는 저장할 현재 버전 값입니다.
- `issuedAt(now)`와 `expiration(expiration)`은 표준 시간 claim이고, `.signWith(secretKey)`가 signature를 붙입니다. `.compact()`의 반환값이 05장 Controller의 JSON body로 나간 token 문자열입니다.
- provider는 localStorage나 Cookie를 직접 변경하지 않습니다. provider의 책임은 서명된 문자열을 반환하는 것이고, 저장 책임은 Controller와 frontend module에 있습니다.

### 코드 바로 아래 설명: 검증 parser

~~~~java
Claims claims = Jwts.parser()
        .verifyWith(secretKey)
        .build()
        .parseSignedClaims(token)
        .getPayload();
~~~~

- `Jwts.parser()`는 JJWT parser builder를 시작합니다. 이 method는 사용자가 만든 helper가 아니라 JJWT library가 제공하는 static factory입니다.
- `verifyWith(secretKey)`는 이 parser가 같은 SecretKey로 token signature를 검증하도록 설정합니다. payload 문자열만 읽는 것이 아니라 서명 검증을 포함합니다.
- `build()`는 설정이 끝난 parser 객체를 완성합니다.
- `parseSignedClaims(token)`은 Filter가 header에서 꺼낸 token 문자열을 signed JWT로 파싱합니다. 형식 오류·서명 불일치·만료 시 `JwtException` 계열이 발생할 수 있습니다.
- `getPayload()`는 검증이 끝난 claim 묶음을 `Claims`로 반환합니다. 검증되지 않은 payload를 Filter에 넘기는 것이 아닙니다.
- `claims.get(AUTH_VERSION_CLAIM)`의 반환 type은 여러 종류의 JWT claim을 담을 수 있는 `Object`입니다. `instanceof Number authVersion`은 숫자 여부를 검사하면서 지역 변수까지 만드는 pattern matching 문법입니다.
- `Long.valueOf(claims.getSubject())`는 subject 문자열을 User ID `Long`으로 바꿉니다. `authVersion.longValue()`는 `Number`를 primitive `long`으로 변환합니다.
- JWT parser가 실패하면 `JwtException | IllegalArgumentException` multi-catch가 이를 `AuthException("Invalid_Token")`으로 바꿉니다. 내부에서 직접 던진 `AuthException`은 이 catch에 다시 잡히지 않고 Filter catch로 전달됩니다.

### 이 method의 호출·반환·예외

- 호출자: `JwtAuthenticationFilter.doFilterInternal`.
- 입력: `Authorization` header에서 `Bearer ` prefix를 제거한 token 문자열.
- 내부 값: 검증된 `Claims`, `authVersionClaim`.
- 반환값: `AccessTokenClaims` → Filter의 User ID 조회와 version 비교.
- 예외: parser/형식/만료/서명 문제 → `AuthException("Invalid_Token")` → Filter가 401 직접 응답.

---

## 5. SecurityConfig가 Filter 위치와 보호 범위를 만든다

### 실제 파일

`/Users/miles/Documents/GitHub/KTB4_Miles_Week12_Back/src/main/java/kr/adapterz/springdatajpa/config/SecurityConfig.java`

SecurityConfig 전체는 새 04장에서 공통 기반으로 읽었으므로 여기서는 Access Token 요청에
직접 연결되는 실제 설정만 발췌합니다.

### 코드 원문: Filter 등록과 인가 규칙

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

- `jwtAuthenticationFilter`는 프로젝트가 만든 `JwtAuthenticationFilter` Bean입니다. `UsernamePasswordAuthenticationFilter.class`는 Spring Security가 제공하는 Filter class이며, 두 번째 인자는 실행 위치 기준이지 사용자가 선언한 class가 아닙니다.
- `addFilterBefore`는 custom Filter가 기준 Filter보다 앞에서 실행되도록 chain에 등록합니다. Controller가 이 method를 직접 호출하는 것이 아닙니다. 애플리케이션 시작 시 `SecurityFilterChain` Bean을 조립할 때 등록됩니다.
- `/sessions`, `/users` 등 공개 endpoint는 `permitAll`이므로 인증 없이 시작할 수 있습니다. 보호 API인 `/posts`는 이 목록에 없어 `anyRequest().authenticated()`가 적용됩니다.
- `shouldNotFilter`의 제외 목록과 `permitAll` 목록이 비슷해 보여도 역할이 다릅니다. 전자는 custom JWT Filter를 실행할지, 후자는 Security 인가를 통과시킬지를 결정합니다.
- 보호 요청에서 Filter가 Authentication을 만들지 못하면 `anyRequest().authenticated()`가 authentication entry point를 실행해 401을 반환합니다. 권한 부족 403은 별도의 access denied handler 경로입니다.

### 보안 흐름의 호출 주체

`SecurityConfig.securityFilterChain`은 Spring 초기화 단계에서 호출되고, `JwtAuthenticationFilter.doFilterInternal`은 이후 실제 HTTP 요청마다 Servlet Filter framework가 호출합니다. 두 method를 Controller나 frontend가 직접 호출한다고 설명하면 안 됩니다.

---

## 6. `JwtAuthenticationFilter`가 header를 현재 요청의 Authentication으로 바꾼다

### 실제 파일

`/Users/miles/Documents/GitHub/KTB4_Miles_Week12_Back/src/main/java/kr/adapterz/springdatajpa/auth/JwtAuthenticationFilter.java`

### 코드 원문: 현재 파일 전체

~~~~java
package kr.adapterz.springdatajpa.auth;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import kr.adapterz.springdatajpa.exception.AuthException;
import kr.adapterz.springdatajpa.exception.DataNullException;
import kr.adapterz.springdatajpa.config.ErrorResponseWriter;
import kr.adapterz.springdatajpa.exception.ApiErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtProvider jwtProvider;
    private final CustomUserDetailsService customUserDetailsService;
    private final ErrorResponseWriter errorResponseWriter;

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

            errorResponseWriter.write(response, org.springframework.http.HttpStatus.UNAUTHORIZED, ApiErrorCode.INVALID_TOKEN);
        }
    }
}
~~~~

### 코드 바로 아래 설명: Filter가 호출되는 조건

- `JwtAuthenticationFilter extends OncePerRequestFilter`는 Spring이 제공하는 base class를 상속합니다. `@Component`로 Bean이 된 뒤 SecurityConfig가 chain에 넣고, Servlet framework가 요청마다 부모 Filter method를 호출합니다.
- `shouldNotFilter`는 method/path 조합을 보고 custom Filter를 건너뛸지 결정합니다. `GET /posts`는 제외 목록에 없으므로 false이고, 부모 class가 `doFilterInternal`을 호출합니다.
- `POST /sessions`, `POST /sessions/refresh`, `DELETE /sessions`, `POST /users`, 모든 `OPTIONS`는 true이므로 이 Filter의 JWT 검증을 건너뜁니다. 이 return은 요청 전체 허용이 아니라 custom Filter만 제외하는 결과입니다.

### 코드 바로 아래 설명: header 없는 요청과 `return`

~~~~java
String authorizationHeader = request.getHeader("Authorization");

if (authorizationHeader == null || !authorizationHeader.startsWith("Bearer ")) {
    filterChain.doFilter(request, response);
    return;
}
~~~~

- `request.getHeader("Authorization")`는 현재 HTTP request header 값을 가져옵니다. header가 없으면 `null`입니다.
- `startsWith("Bearer ")`는 문자열이 정확히 `Bearer`와 공백으로 시작하는지 확인하는 Java `String` method입니다. 이 Filter가 임의의 Basic token이나 다른 header 형식을 JWT로 해석하지 않는 조건입니다.
- 조건이 true이면 `filterChain.doFilter(request, response)`를 먼저 호출합니다. 이 호출이 다음 Filter, Security 인가, DispatcherServlet/Controller로 계속 진행되는 통로입니다.
- 그 다음 `return`은 현재 `doFilterInternal` method만 종료합니다. 이미 호출한 `filterChain` 내부의 다음 처리까지 취소하지 않습니다. `doFilter` 없이 return하면 현재 Filter가 요청을 끊지만, 이 코드에서는 먼저 chain을 호출했기 때문에 다음 단계가 계속됩니다.
- header가 없는 보호 요청은 Filter에서 401을 직접 만들지 않습니다. Authentication이 만들어지지 않은 상태로 Security 인가까지 가고, `anyRequest().authenticated()`가 authentication entry point를 실행해 401 `UNAUTHORIZED`를 만듭니다.

### 코드 바로 아래 설명: token을 claims로 바꾸고 User를 다시 조회

~~~~java
String token = authorizationHeader.substring(7);
AccessTokenClaims tokenClaims = jwtProvider.getAccessTokenClaims(token);

CustomUserDetails userDetails =
        customUserDetailsService.loadUserByUserId(tokenClaims.userId());
~~~~

- `substring(7)`은 `Bearer ` 7글자를 제거하고 실제 JWT 문자열만 남깁니다. 앞에서 prefix를 확인했기 때문에 이 위치를 자릅니다.
- `jwtProvider.getAccessTokenClaims(token)`은 서명·만료·claim 형식이 검증된 결과를 반환합니다. token에 User 정보가 있다고 해서 바로 principal로 쓰는 것이 아니라, subject의 ID로 현재 DB User를 다시 조회합니다.
- `CustomUserDetailsService.loadUserByUserId`는 삭제되지 않은 User만 조회하고, 없으면 `DataNullException("No_User")`을 던집니다. JWT가 유효해도 삭제된 User를 계속 인증하지 않기 위한 두 번째 확인입니다.

### 코드 바로 아래 설명: 현재 User 상태와 `authVersion` 비교

~~~~java
if (!userDetails.isEnabled()
        || userDetails.getAuthVersion() != tokenClaims.authVersion()) {
    throw new DataNullException("No_User");
}
~~~~

- `isEnabled()`는 `!user.isDeleted() && !user.isSuspended()`를 반환합니다. deleted 또는 신고 기준 이상 suspended이면 false입니다.
- `userDetails.getAuthVersion()`은 DB User의 현재 version이고, `tokenClaims.authVersion()`은 token 발급 당시 custom claim입니다.
- 비밀번호 변경 시 `User.changePassword`가 `authVersion++`을 수행합니다. 그러면 기존 token의 version과 DB version이 달라져, token signature가 아직 유효하고 만료되지 않았어도 이 Filter에서 거부됩니다.
- 두 조건은 `||`이므로 하나라도 실패하면 `DataNullException`을 던집니다. 이 예외는 아래 multi-catch로 이동해 401 `INVALID_TOKEN`이 됩니다.

### 코드 바로 아래 설명: Authentication 객체 생성

~~~~java
UsernamePasswordAuthenticationToken authentication =
        new UsernamePasswordAuthenticationToken(
                userDetails,
                null,
                userDetails.getAuthorities()
        );
authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
~~~~

- `UsernamePasswordAuthenticationToken`은 Spring Security가 제공하는 `Authentication` 구현 class입니다. 로그인 시에는 email/password 입력을 담을 수 있지만, 이 Filter에서는 이미 검증된 `userDetails`를 principal로 넣습니다.
- 첫 번째 인자 `userDetails`는 이후 Controller의 `@AuthenticationPrincipal`로 주입될 객체입니다. 두 번째 `null`은 이 JWT 인증 결과가 password credential을 보관하지 않음을 뜻합니다.
- 세 번째 `getAuthorities()`는 현재 code에서 `ROLE_USER` 하나를 반환합니다. Security 인가에서 권한이 필요할 때 읽을 값입니다.
- `new WebAuthenticationDetailsSource().buildDetails(request)`는 Spring Security 제공 class가 현재 request의 부가 정보를 `authentication.details`에 연결합니다. 현재 project source에서 이 details를 별도로 읽는 호출은 확인되지 않았습니다.

### 코드 바로 아래 설명: `SecurityContextHolder`와 chain 계속

~~~~java
SecurityContextHolder.getContext().setAuthentication(authentication);

filterChain.doFilter(request, response);
~~~~

- `SecurityContextHolder`는 현재 실행 중인 요청의 `SecurityContext`에 접근하는 Spring Security 진입점입니다. `setAuthentication`이 실행되어야 뒤의 Security 인가가 이 요청을 인증된 것으로 판단합니다.
- 이 저장은 localStorage나 DB에 token을 저장하는 동작이 아닙니다. frontend localStorage는 browser storage이고, `SecurityContext`는 현재 backend request 처리에 사용할 인증 결과입니다.
- `filterChain.doFilter`는 Filter가 Controller를 직접 호출하는 것이 아니라 다음 Filter와 DispatcherServlet에게 요청을 넘기는 호출입니다. 그 다음 Controller가 mapping에 따라 호출됩니다.

### 코드 바로 아래 설명: 실패 시 context를 지우고 직접 응답

~~~~java
} catch (AuthException | DataNullException e) {
    SecurityContextHolder.clearContext();

    errorResponseWriter.write(
            response,
            org.springframework.http.HttpStatus.UNAUTHORIZED,
            ApiErrorCode.INVALID_TOKEN
    );
}
~~~~

- `AuthException | DataNullException`은 Java multi-catch입니다. JWT parser 오류, 현재 User 없음, 삭제·정지·version 불일치를 하나의 인증 실패 경로에서 받습니다.
- `clearContext()`는 중간에 저장된 인증 정보가 남아 있지 않도록 현재 context를 비웁니다. User Entity, Access Token localStorage, Refresh Cookie를 삭제하는 method가 아닙니다.
- `errorResponseWriter.write`는 response status·content type·JSON body를 직접 기록합니다. 이 catch에서는 `filterChain.doFilter`를 호출하지 않았으므로 Controller와 `GlobalExceptionHandler`에 도달하지 않습니다.
- Filter 오류 code는 `INVALID_TOKEN`입니다. Controller/Service에서 발생한 `AuthException`은 다른 경계인 `GlobalExceptionHandler`에서 `INVALID_REFRESH_TOKEN` 등으로 변환될 수 있습니다.

### 이 Filter의 메서드 계약

| 메서드 | 호출자 | 입력 | 반환/효과 | 예외·다음 단계 |
|---|---|---|---|---|
| `shouldNotFilter` | `OncePerRequestFilter` 부모 framework | Servlet request | true면 이 Filter의 내부 검증 생략 | 다음 Filter 처리 계속 |
| `doFilterInternal` | 부모 Filter framework | request/response/filterChain | 성공 시 SecurityContext에 Authentication 저장 | 성공은 chain, 실패는 401 직접 응답 |
| `jwtProvider.getAccessTokenClaims` | `doFilterInternal` | Bearer prefix 제거 token | `AccessTokenClaims` | `AuthException`이면 catch |
| `loadUserByUserId` | `doFilterInternal` | token의 userId | `CustomUserDetails` | `DataNullException`이면 catch |
| `filterChain.doFilter` | Filter | 같은 request/response | 다음 Filter·DispatcherServlet 실행 | Controller까지 이어질 수 있음 |

---

## 7. `CustomUserDetailsService.loadUserByUserId`가 token ID로 현재 User를 확인한다

### 실제 파일

`/Users/miles/Documents/GitHub/KTB4_Miles_Week12_Back/src/main/java/kr/adapterz/springdatajpa/auth/CustomUserDetailsService.java`

### 코드 원문: 관련 class와 두 lookup method

05장에서 `loadUserByUsername`의 로그인 호출을 설명했으므로, 여기서는 같은 class의
User ID lookup을 중심으로 실제 source를 다시 봅니다.

~~~~java
@Service
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    @Override
    public CustomUserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        User user = userRepository.findByEmailAndDeletedFalse(email)
                .orElseThrow(() -> new UsernameNotFoundException("No_User"));

        return new CustomUserDetails(user);
    }

    public CustomUserDetails loadUserByUserId(Long userId) {
        User user = userRepository.findByUserIdAndDeletedFalse(userId)
                .orElseThrow(() -> new DataNullException("No_User"));

        return new CustomUserDetails(user);
    }
}
~~~~

### 코드 바로 아래 설명

- `loadUserByUserId`의 호출자는 Controller가 아니라 `JwtAuthenticationFilter.doFilterInternal`입니다. Filter가 JWT subject에서 얻은 ID를 넘깁니다.
- `findByUserIdAndDeletedFalse`는 `userId` equality와 `deleted=false` 조건을 가진 Spring Data derived query입니다. 결과가 없으면 `Optional.orElseThrow`가 `DataNullException("No_User")`을 던집니다.
- 조회된 `User`를 `new CustomUserDetails(user)`로 감싸 반환합니다. Filter는 Entity를 직접 Security principal로 넣지 않고 `UserDetails` wrapper를 사용합니다.
- `loadUserByUsername`은 로그인 `AuthenticationManager`가 호출하는 다른 method입니다. 이름이 비슷하지만 입력이 email이고, `loadUserByUserId`는 이미 검증된 token의 ID를 사용한다는 차이가 있습니다.

### 입력과 반환

`AccessTokenClaims.userId()` → `loadUserByUserId(Long userId)` → `Optional<User>` →
`CustomUserDetails` → Filter의 `userDetails` 변수입니다. `userDetails`가 null인 정상 경로는
없고, User가 없으면 예외로 분기합니다.

---

## 8. `CustomUserDetails`와 `User`가 현재 인증 가능 상태를 계산한다

### 8.1 `CustomUserDetails` 실제 원문

### 실제 파일

`/Users/miles/Documents/GitHub/KTB4_Miles_Week12_Back/src/main/java/kr/adapterz/springdatajpa/auth/CustomUserDetails.java`

### 코드 원문

~~~~java
package kr.adapterz.springdatajpa.auth;

import kr.adapterz.springdatajpa.entity.User;
import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;

@Getter
public class CustomUserDetails implements UserDetails {

    private final User user;

    public CustomUserDetails(User user) {
        this.user = user;
    }

    public Long getUserId() {
        return user.getUserId();
    }

    public String getNickname() {
        return user.getNickname();
    }

    public long getAuthVersion() {
        return user.getAuthVersion();
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_USER"));
    }

    @Override
    public String getPassword() {
        return user.getPassword();
    }

    @Override
    public String getUsername() {
        return user.getEmail();
    }

    @Override
    public boolean isEnabled() {
        return !user.isDeleted() && !user.isSuspended();
    }

}
~~~~

### 코드 바로 아래 설명

- `CustomUserDetails`는 DB Entity `User`를 Spring Security `UserDetails` 계약으로 감싸는 wrapper입니다. Filter가 `userDetails`를 principal로 넣는 이유가 이 interface 계약 때문입니다.
- `getUserId()`와 `getAuthVersion()`은 내부 `User` field를 그대로 반환합니다. Filter가 각각 version 비교와 Controller 전달에 사용합니다.
- `getAuthorities()`는 현재 모든 User에게 `ROLE_USER`를 반환합니다. 권한을 세분화하는 role table이나 admin role은 이 class에 없습니다.
- `getUsername()`은 email을 반환하지만, 보호 요청에서는 이 method보다 `getUserId()`와 `getAuthorities()`가 실제 흐름에 직접 쓰입니다.
- `isEnabled()`는 deleted와 suspended를 함께 확인합니다. Filter가 token 검증 후 다시 호출해 현재 상태를 확인합니다.

### 8.2 `User`의 deleted·suspended·authVersion 실제 위치

### 실제 파일과 코드 발췌

`/Users/miles/Documents/GitHub/KTB4_Miles_Week12_Back/src/main/java/kr/adapterz/springdatajpa/entity/User.java`

~~~~java
@Column(name ="deleted", nullable = false)
private boolean deleted;

@Column(name = "auth_version", nullable = false)
private long authVersion;

public boolean isSuspended() {
    return receivedReportCount >= SUSPENSION_REPORT_THRESHOLD;
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

- `deleted`는 soft delete 상태입니다. `findByUserIdAndDeletedFalse`가 이미 이 값을 query 조건으로 사용하므로 삭제된 User는 정상 lookup에서 나오지 않습니다.
- `isSuspended()`는 `receivedReportCount >= SUSPENSION_REPORT_THRESHOLD`를 계산합니다. 신고 누적 정지 판단은 token parser가 아니라 DB User 상태 확인 단계에서 일어납니다.
- `authVersion`은 password 변경 시 증가합니다. 기존 JWT에는 발급 당시 숫자가 남아 있으므로, Filter가 DB 현재 값과 비교해 오래된 token을 거부할 수 있습니다.
- `delete()`는 User row와 JWT를 즉시 삭제하지 않습니다. `deleted=true`를 기록하고 표시용 field를 바꿉니다. 다음 보호 요청에서 User lookup 또는 `isEnabled`가 실패하는 구조입니다.
- `changePassword`의 `authVersion++`은 현재 로그인 중인 User의 DB 상태를 바꾸는 method입니다. 이 장의 Filter는 그 변경을 다음 요청에서 감지합니다. 실제 비밀번호 변경 HTTP 흐름은 새 08장에서 다룹니다.

### 상태 비교의 의미

~~~~text
token.authVersion = 3
DB User.authVersion = 3
→ 현재 token version 일치, 다음 단계

token.authVersion = 3
DB User.authVersion = 4
→ 비밀번호 변경 등으로 token이 오래됨, INVALID_TOKEN 401
~~~~

Token signature가 유효하다는 것은 “발급자가 만든 token이 변조되지 않았다”는 뜻이고,
`authVersion` 일치는 “그 token이 현재 User 상태에서도 허용되는가”를 확인하는 별도 단계입니다.

---

## 9. 보호 Controller가 `SecurityContext`의 principal을 받는다

### 실제 파일

`/Users/miles/Documents/GitHub/KTB4_Miles_Week12_Back/src/main/java/kr/adapterz/springdatajpa/controller/PostController.java`

### 코드 원문: 상세 조회와 작성의 보호 parameter

package/import와 게시글의 다른 mutation method는 principal 주입을 확인하는 데 필요하지
않아 생략한 실제 source 발췌입니다. `getPostView`와 `createPost`의 parameter·호출 body는
현재 파일과 동일하게 적고, 전체 게시글 업무는 09~16장에서 읽습니다.

~~~~java
@RestController
@RequestMapping("/posts")
@RequiredArgsConstructor
public class PostController {
    private final PostService postService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public PostResponseDto createPost(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Valid @RequestBody PostRequestDto request
    ){
        return postService.createPost(userDetails.getUserId(), request);
    }

    @GetMapping("/{postId}")
    public PostViewResponseDto getPostView(
            @PathVariable("postId") Long postId,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        return postService.getPostView(postId, userDetails.getUserId());
    }
}
~~~~

### 코드 바로 아래 설명

- `@AuthenticationPrincipal`은 Spring Security의 현재 `Authentication.getPrincipal()`을 method parameter에 넣어 달라는 annotation입니다. Controller가 request header를 다시 파싱하거나 User ID를 직접 받지 않습니다.
- 이 parameter에 실제로 들어가는 객체는 Filter가 만든 `CustomUserDetails`입니다. Filter에서 `new UsernamePasswordAuthenticationToken(userDetails, ...)`의 첫 번째 인자로 넣었기 때문입니다.
- `getPostView`의 `postId`는 URL path에서 오고, `userDetails.getUserId()`는 SecurityContext principal에서 옵니다. 두 값의 출처가 다릅니다.
- Controller는 `postService.getPostView(postId, userDetails.getUserId())`를 호출합니다. Filter가 User ID를 Service에 직접 전달하는 것이 아니며, Controller가 principal에서 꺼내 Service argument로 변환합니다.
- `createPost`도 같은 principal 전달 구조를 사용합니다. endpoint가 달라도 `@AuthenticationPrincipal` parameter의 값은 같은 요청 context에서 옵니다.
- Authentication이 없는데 보호 Controller까지 도달하려고 하면 `@AuthenticationPrincipal`에 정상 `CustomUserDetails`가 들어갈 수 없습니다. 일반적으로 그 전 단계의 Security 인가가 401로 막습니다.

### 상세 조회 이후 Service 연결

`PostService.getPostView`는 `loginUserId`를 사용해 현재 User와 좋아요·신고 여부를 확인하고,
조회수 증가 같은 게시글 업무를 처리합니다. 이 장에서는 Access Token이 `userDetails.getUserId()`로
바뀌어 Service 입력이 되는 지점만 확인하고, 게시글·조회수 업무는 09~16장에서 읽습니다.

---

## 10. 오류가 어느 계층에서 끝나는지 구분한다

### 10.1 header 없음 또는 Bearer 아님

~~~~text
Authorization header 없음
또는 "Basic ...", "Token ..."처럼 Bearer로 시작하지 않음
→ JwtAuthenticationFilter가 검증하지 않음
→ filterChain.doFilter로 다음 단계 진행
→ 보호 endpoint라면 authenticated() 실패
→ SecurityConfig.authenticationEntryPoint
→ ErrorResponseWriter
→ 401 UNAUTHORIZED
~~~~

이 경우 Filter가 `INVALID_TOKEN`을 직접 쓰지 않습니다. Filter는 “인증 결과를 만들지 않은
상태로 통과”시켰고, 보호 endpoint인지 판단하는 Security 인가가 뒤에서 401을 만듭니다.

### 10.2 Bearer token parser 오류

~~~~text
Bearer prefix 확인
→ JwtProvider.getAccessTokenClaims
→ 서명 불일치·만료·형식·claim 변환 오류
→ AuthException("Invalid_Token")
→ Filter catch
→ SecurityContext.clearContext
→ ErrorResponseWriter
→ 401 INVALID_TOKEN
~~~~

이 경우에는 Filter가 `filterChain.doFilter`를 호출하지 않습니다. 따라서 Controller와
`GlobalExceptionHandler`까지 요청이 내려가지 않습니다.

### 10.3 현재 User 조회·상태·version 오류

~~~~text
JWT claims에서 userId 추출
→ findByUserIdAndDeletedFalse
→ User 없음 / deleted / suspended / authVersion 불일치
→ DataNullException("No_User")
→ Filter catch
→ 401 INVALID_TOKEN
~~~~

현재 source에서 User 상태 오류를 `No_User`라는 내부 code로 통합해 Filter가
`INVALID_TOKEN`으로 응답합니다. 이 문서에서 “삭제 계정은 다른 status로 응답한다”고
추측하지 않습니다.

### 10.4 인증 성공 후 권한 부족

현재 `CustomUserDetails.getAuthorities()`가 `ROLE_USER` 하나를 반환하고, 실제
`SecurityConfig`에 role별 matcher는 없습니다. 따라서 이 프로젝트의 일반 보호 요청은
인증 유무가 핵심입니다. 그래도 Security framework가 인증된 principal의 authority를
검사하다가 권한 부족을 판단하면 `accessDeniedHandler`가 403 `FORBIDDEN`을 기록합니다.
이 경로는 token parser 오류인 `INVALID_TOKEN`과 다른 경계입니다.

### 오류 경계 비교

| 상황 | Controller 도달 | 응답 작성자 | status/code |
|---|---:|---|---|
| header 없음, 보호 API | 아니오 | Security `authenticationEntryPoint` → `ErrorResponseWriter` | 401 `UNAUTHORIZED` |
| Bearer 아님, 보호 API | 아니오 | Security `authenticationEntryPoint` | 401 `UNAUTHORIZED` |
| 서명·만료·claim 오류 | 아니오 | Filter catch → `ErrorResponseWriter` | 401 `INVALID_TOKEN` |
| User 없음/삭제/정지/version 불일치 | 아니오 | Filter catch → `ErrorResponseWriter` | 401 `INVALID_TOKEN` |
| 인증 성공·권한 부족 | 아니오 | Security `accessDeniedHandler` | 403 `FORBIDDEN` |
| 인증 성공 | 예 | Controller return → JSON | endpoint별 status |

---

## 11. 관련 테스트가 각각 증명하는 범위

### 11.1 `JwtProviderTest`: 실제 key·signature·expiration을 검사한다

### 실제 파일

`/Users/miles/Documents/GitHub/KTB4_Miles_Week12_Back/src/test/java/kr/adapterz/springdatajpa/auth/JwtProviderTest.java`

### 코드 원문: 핵심 테스트

~~~~java
@Test
void 발급한_액세스_토큰에서_사용자_ID와_인증_버전을_다시_꺼낼_수_있다() {
    JwtProvider jwtProvider = new JwtProvider(SECRET, ONE_HOUR_MILLIS);

    String accessToken = jwtProvider.createAccessToken(42L, 3L);
    AccessTokenClaims tokenClaims =
            jwtProvider.getAccessTokenClaims(accessToken);

    assertThat(accessToken).isNotBlank();
    assertThat(tokenClaims.userId()).isEqualTo(42L);
    assertThat(tokenClaims.authVersion()).isEqualTo(3L);
}

@Test
void 다른_비밀키로_서명한_토큰은_Invalid_Token_예외가_발생한다() {
    JwtProvider tokenIssuer = new JwtProvider(
            "forged-jwt-secret-key-must-be-at-least-32-characters",
            ONE_HOUR_MILLIS
    );
    JwtProvider tokenVerifier = new JwtProvider(SECRET, ONE_HOUR_MILLIS);
    String forgedToken = tokenIssuer.createAccessToken(42L, 0L);

    assertThatThrownBy(() -> tokenVerifier.getAccessTokenClaims(forgedToken))
            .isInstanceOf(AuthException.class)
            .hasMessage("Invalid_Token");
}

@Test
void 만료된_토큰은_Invalid_Token_예외가_발생한다() {
    JwtProvider jwtProvider = new JwtProvider(SECRET, -1_000L);
    String expiredToken = jwtProvider.createAccessToken(42L, 0L);

    assertThatThrownBy(() -> jwtProvider.getAccessTokenClaims(expiredToken))
            .isInstanceOf(AuthException.class)
            .hasMessage("Invalid_Token");
}
~~~~

### 코드 바로 아래 설명

- 첫 테스트는 실제 `JwtProvider` constructor와 JJWT library를 사용해 발급 후 다시 parse합니다. `AccessTokenClaims.userId()`와 `authVersion()`이 원래 입력값으로 돌아오는지 확인합니다.
- 다른 key 테스트는 issuer와 verifier의 SecretKey가 다르면 signature 검증이 실패하는지 확인합니다. Filter의 401 응답까지는 이 단위 테스트가 확인하지 않습니다.
- 만료 테스트는 expiration이 과거가 되도록 `-1_000L`을 주고 parser가 `Invalid_Token`을 던지는지 확인합니다.

### 11.2 `JwtAuthenticationFilterTest`: Filter 분기와 context를 검사한다

### 실제 파일

`/Users/miles/Documents/GitHub/KTB4_Miles_Week12_Back/src/test/java/kr/adapterz/springdatajpa/auth/JwtAuthenticationFilterTest.java`

### 코드 원문: 정상 인증과 version 불일치 테스트의 핵심

~~~~java
@Test
void 유효한_JWT면_사용자_인증_정보를_SecurityContext에_저장한다() throws Exception {
    String accessToken = "valid-access-token";
    User user = new User(
            "test@test.com",
            "encoded-password",
            "tester",
            "profile.png",
            0
    );
    ReflectionTestUtils.setField(user, "userId", 42L);
    CustomUserDetails userDetails = new CustomUserDetails(user);

    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/posts");
    request.addHeader("Authorization", "Bearer " + accessToken);
    MockHttpServletResponse response = new MockHttpServletResponse();
    MockFilterChain filterChain = new MockFilterChain();

    when(jwtProvider.getAccessTokenClaims(accessToken))
            .thenReturn(new AccessTokenClaims(42L, 0L));
    when(customUserDetailsService.loadUserByUserId(42L)).thenReturn(userDetails);

    jwtAuthenticationFilter.doFilter(request, response, filterChain);

    assertThat(response.getStatus()).isEqualTo(200);
    assertThat(SecurityContextHolder.getContext().getAuthentication())
            .isNotNull();
    assertThat(SecurityContextHolder.getContext().getAuthentication().getPrincipal())
            .isEqualTo(userDetails);
}

@Test
void JWT_인증_버전이_현재_사용자와_다르면_401_Invalid_Token을_반환한다() throws Exception {
    User user = new User(
            "test@test.com",
            "encoded-password",
            "tester",
            "profile.png",
            0
    );
    ReflectionTestUtils.setField(user, "userId", 42L);
    user.changePassword("new-encoded-password");
    CustomUserDetails userDetails = new CustomUserDetails(user);

    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/posts");
    request.addHeader("Authorization", "Bearer stale-access-token");
    MockHttpServletResponse response = new MockHttpServletResponse();

    when(jwtProvider.getAccessTokenClaims("stale-access-token"))
            .thenReturn(new AccessTokenClaims(42L, 0L));
    when(customUserDetailsService.loadUserByUserId(42L)).thenReturn(userDetails);

    jwtAuthenticationFilter.doFilter(
            request,
            response,
            new MockFilterChain()
    );

    assertThat(response.getStatus()).isEqualTo(401);
    assertThat(response.getContentAsString()).contains("INVALID_TOKEN");
    assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
}
~~~~

### 코드 바로 아래 설명

- 이 테스트는 `JwtProvider`와 `CustomUserDetailsService`를 Mockito mock으로 대체합니다. 따라서 Filter가 collaborator를 어떤 순서로 호출하고 context에 어떤 principal을 저장하는지는 확인하지만, 실제 JWT parser key 검증은 `JwtProviderTest`의 책임입니다.
- 정상 테스트의 request는 `/posts`이고 header는 `Bearer valid-access-token`입니다. mock claims와 User를 제공했기 때문에 Filter가 Authentication을 만들고 chain을 통과합니다.
- version 불일치 테스트는 `user.changePassword`로 User의 DB version을 1로 만들고 token claim은 0으로 반환합니다. 이 Filter가 version 비교를 수행해 401로 끝내며 context가 null인지 확인합니다.
- 테스트 클래스의 `@AfterEach clearSecurityContext()`는 한 테스트에서 만든 Authentication이 다음 테스트로 남지 않게 합니다. `SecurityContextHolder`는 테스트 전역 static처럼 보일 수 있어 정리가 필요합니다.

### 11.3 기타 인증 테스트

#### `CustomUserDetailsServiceTest`

`/Users/miles/Documents/GitHub/KTB4_Miles_Week12_Back/src/test/java/kr/adapterz/springdatajpa/auth/CustomUserDetailsServiceTest.java`

- `이메일에_맞는_유저가_없으면_UsernameNotFoundException이_발생한다`: email query가 `Optional.empty()`일 때 login lookup이 `No_User` 예외를 내는지 확인합니다.
- `이메일에_맞는_유저가_있으면_CustomUserDetails를_반환한다`: Repository User가 wrapper로 바뀌고 `getUserId`, `getUsername`, `getPassword`가 Entity 값을 반환하는지 확인합니다.
- 이 단위 테스트는 `loadUserByUserId`의 실제 DB query나 Spring Security Filter chain을 실행하지 않습니다.

#### `SecurityConfigTest`

`/Users/miles/Documents/GitHub/KTB4_Miles_Week12_Back/src/test/java/kr/adapterz/springdatajpa/config/SecurityConfigTest.java`

- `인증_없이_보호_API에_접근하면_401_Unauthorized를_반환한다`: `GET /posts`에 인증 없이 접근하면 `UNAUTHORIZED` JSON이 되는지 확인합니다.
- `로그인_API는_인증_없이_접근할_수_있다`: 허용 Origin의 `POST /sessions`가 Security 인증 부족으로 401이 되지 않고 DTO validation 400까지 도달하는지 확인합니다.
- Origin 없음·허용되지 않은 Origin의 session request가 403 `FORBIDDEN_ORIGIN`이 되는지도 확인합니다.
- 이 테스트 파일에는 유효한 JWT를 발급해 실제 `@AuthenticationPrincipal`까지 연결하는 assertion은 없습니다. 그 범위는 현재 `JwtAuthenticationFilterTest`와 실제 애플리케이션 실행을 구분해서 봐야 합니다.

### 테스트 범위 요약

| 테스트 | 실제 확인 | 확인하지 않음 |
|---|---|---|
| `JwtProviderTest` | key·signature·expiration·claims | Filter response·DB User 조회 |
| `JwtAuthenticationFilterTest` | header 분기·claims 결과 사용·context·version mismatch | 실제 JWT parser·실제 DB·Controller |
| `CustomUserDetailsServiceTest` | Repository 결과와 wrapper/예외 | 실제 AuthenticationManager 연결 |
| `SecurityConfigTest` | Filter chain·인가·401/Origin 403 | 유효 JWT가 보호 Controller까지 가는 전체 context |

이 문서 작성 중 테스트 명령은 실행하지 않았습니다. 위 내용은 실제 test source의 method와
assertion을 읽어 정리한 것입니다.

---

## 12. 보호 요청 전체를 값의 이동으로 다시 연결한다

| 단계 | 값 | 선언·생성 위치 | 다음 사용 위치 |
|---|---|---|---|
| 1 | Access Token 문자열 | 05장 `JwtProvider.createAccessToken` | `SessionResponseDto` → `authStorage.setAccessToken` |
| 2 | localStorage 값 | browser `accessToken` key | `authStorage.getAccessToken` |
| 3 | Authorization header | `api.js.sendRequest` | Servlet request header |
| 4 | raw token | `JwtAuthenticationFilter.substring(7)` | `JwtProvider.getAccessTokenClaims` |
| 5 | `AccessTokenClaims` | `JwtProvider` parser 결과 | User ID query·version 비교 |
| 6 | User Entity | `UserRepository.findByUserIdAndDeletedFalse` | `CustomUserDetails` |
| 7 | principal wrapper | `new CustomUserDetails(user)` | `UsernamePasswordAuthenticationToken` |
| 8 | Authentication | Filter constructor | `SecurityContextHolder` |
| 9 | `CustomUserDetails` parameter | `@AuthenticationPrincipal` | `PostController` → `PostService` |
| 10 | API response | Controller return | frontend state/화면 |

### 정상 흐름

~~~~text
LoginPage가 저장한 accessToken
→ api.js가 Bearer header 생성
→ Filter가 token을 claims로 검증
→ claims.userId로 현재 User 조회
→ DB version과 token version 비교
→ Authentication principal 저장
→ chain을 통해 Controller 호출
→ Controller가 principal에서 userId 추출
→ Service가 userId로 업무 수행
~~~~

### 실패 흐름

~~~~text
header 없음/형식 불일치
→ chain은 계속되지만 protected endpoint에서 Security 401

Bearer token parser/User 상태/version 실패
→ Filter가 context 삭제
→ Filter가 직접 INVALID_TOKEN 401

처음 요청 401
→ frontend api.js가 Refresh 경계로 이동
→ 새 Access Token 성공 시 원래 request 재시도
~~~~

마지막 Refresh 재시도 줄은 이 장의 backend Filter가 수행하는 일이 아닙니다. frontend
`api.js`가 401을 보고 별도의 `/sessions/refresh` 요청을 만드는 후속 흐름입니다. 새 07장에서
Cookie·hash·rotation·동시 요청을 처음부터 끝까지 설명합니다.

## 13. 스킵 또는 다음 장에서 다시 읽을 코드

- `api.js`의 `performRefresh`, `refreshAccessToken`: 실제 source와 호출 위치는 확인했지만 Refresh Session의 업무 흐름이므로 새 07장에 전체적으로 배치합니다. 이 장에서는 401 후 이동 지점만 설명합니다.
- `RefreshTokenProvider`, `RefreshCookieProvider`, `AuthSession`, `AuthSessionRepository`: Access Token 보호 요청에는 직접 호출되지 않습니다. Refresh Token Cookie와 DB row lock은 새 07장입니다.
- `SessionController.refreshSession`, `deleteSession`: 공개 endpoint라 Filter가 건너뛰는 연결만 새 04/이 장에서 확인하고, 실제 method body는 새 07·08장에서 읽습니다.
- `PostService.getPostView` 내부의 comments·like·report·조회수 처리: Controller까지 principal이 전달된 뒤의 게시글 업무이며 09~16장의 책임입니다. 이 장에서는 `userDetails.getUserId()`가 Service parameter가 되는 지점만 확인합니다.
- `GlobalExceptionHandler` 전체: 03장과 새 04장에서 전체 구조를 읽었습니다. 이 장의 Filter 오류는 직접 `ErrorResponseWriter`로 끝나므로 Controller handler로 가지 않는 차이만 다시 설명했습니다.
- CSS·PostDetailCard·CommentSection: 화면 표시와 후속 게시글 기능을 담당하며 Access Token이 header로 들어가는 경로를 바꾸지 않습니다.

스킵은 “현재 코드에서 사용되지 않음”과 다릅니다. `api.js.performRefresh`처럼 실제로 사용되는
코드도 실행 흐름의 소유 장이 달라 이 장에서는 중복하지 않았습니다. 반대로 현재 source에서
호출 위치가 확인되지 않는 `authApi.refresh()`는 05장에서 “현재 흐름에서 사용되지 않음”으로
표시했으며, 이 장에서도 사용된다고 가정하지 않습니다.

## 14. 새로 등장한 문법·개념과 앞 장에서 본 문법

### 이 장에서 처음 자세히 연결한 개념

- `SecurityContextHolder`: 현재 요청의 Authentication을 보관하는 Spring Security 진입점
- `UsernamePasswordAuthenticationToken`을 로그인 입력이 아닌 JWT 인증 결과 wrapper로 사용하는 방식
- `FilterChain.doFilter`와 현재 Filter method의 `return`이 서로 다른 종료 의미를 갖는 이유
- `@AuthenticationPrincipal`이 context principal을 Controller parameter에 주입하는 과정
- JWT 서명 검증과 DB User 상태·`authVersion` 검증이 서로 다른 단계라는 점
- route guard의 문자열 존재 확인과 backend 인증의 차이

### 05장·새 04장에서 이미 본 문법

- React state, `localStorage`, `async/await`, `fetch`, `request` 공통 module
- Spring `@Component`, constructor injection, `@Value`, `@RestController`, `@RequestMapping`
- `ErrorResponseWriter`, HTTP response status/content type/JSON 작성
- Lombok `@Getter`, `@RequiredArgsConstructor`, Java `Optional.orElseThrow`
- `CustomUserDetails` 전체 구조와 `Refresh`가 아닌 Access Token에서의 재사용

앞 장에서 설명한 문법이라도 이 장의 매개변수·반환값·현재 실행 순서가 달라지는 곳은 생략하지
않았습니다. 예를 들어 `return`은 일반 Java method에서 끝내는 것과 Filter에서 chain 호출 후
끝내는 것이 다르므로 보호 요청 코드 바로 아래에서 다시 설명했습니다.

## 진행 상태

- 공식 파일 진행도: **52/217 (약 24.0%)**
- 산정 근거: 새 04·05장에서 기존 인증 범위의 52개 파일까지 확인한 기준을 유지합니다. `AccessTokenClaims.java`, `JwtProvider.java`, `JwtAuthenticationFilter.java`, `SecurityConfig.java`를 이 문서에서 다시 정독했지만, 이미 확인된 파일을 중복 집계하지 않았습니다.
- 이 장에서 전체 원문 또는 정확한 흐름 발췌로 확인한 파일: `authStorage.js`, `api.js`의 보호 요청 구간, `ProtectedRoute.jsx`, `AppRoutes.jsx`의 보호 route 구간, `PostDetailPage.jsx`의 상세 조회 effect, `postApi.js`의 `getPost`, `AccessTokenClaims.java`, `JwtProvider.java`, `SecurityConfig.java`의 관련 설정, `JwtAuthenticationFilter.java`, `CustomUserDetailsService.java`, `CustomUserDetails.java`, `User.java`의 인증 상태 method, `PostController.java`, `JwtProviderTest.java`, `JwtAuthenticationFilterTest.java`, `CustomUserDetailsServiceTest.java`, `SecurityConfigTest.java`.
- 문서 작성 상태: Access Token 정상·실패·Controller 전달·테스트 범위 작성 완료.
- 사용자 이해 checkpoint: `localStorage → Authorization header → Filter → Claims → User → Authentication → SecurityContext → @AuthenticationPrincipal` 연결을 확인할 차례입니다.
- 다음 학습 시작점: 새 `07_인증_RefreshToken_재발급과_세션Rotation_전체흐름.md`의 `api.js` 401 경계와 `/sessions/refresh` Controller.
- 실행하지 않은 검증: backend/frontend 테스트, Spring context 실행, 실제 browser request, DB query/transaction, Redis, Docker, 배포.
