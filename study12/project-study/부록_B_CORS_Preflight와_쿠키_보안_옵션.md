# 부록 B. CORS Preflight와 쿠키 보안 옵션

> 5장의 `SecurityConfig`에 등장한 `permitAll` OPTIONS 한 줄과 12장 nginx의 `proxy_set_header` 라인이 왜 필요한지, 브라우저가 실제로 어떤 요청을 보내는지 분 단위로 짚는다.

## B.1 학습 목표

```text
왜 OPTIONS preflight가 필요한가
→ 현재 코드에서 preflight가 통과되는 경로
→ HttpOnly + SameSite=Strict + Secure의 의미
→ credentials: "include"가 만드는 추가 제약
→ nginx가 Set-Cookie를 그대로 전달하지 않는 이유
```

## B.2 CORS가 필요한 이유

같은 출처(same-origin) 정책에 따라 브라우저는 다른 origin으로의 요청을 기본적으로 차단한다. 프론트(`http://week12-fe:80`)와 백엔드(`http://week12-be:8080`)는 origin이 다르므로 **이대로는 axios/fetch가 백엔드 요청을 보낼 수 없다**.

이걸 풀어주는 것이 CORS(Cross-Origin Resource Sharing) 표준이다. 서버가 `Access-Control-Allow-Origin: ...` 헤더로 "이 출처는 허용한다"고 명시하면 브라우저가 통과시킨다.

## B.3 Simple Request vs Preflight

브라우저가 origin 간 요청을 보낼 때 두 갈래 길이 있다.

### Simple Request (preflight 없음)

다음 조건을 모두 만족하면 본 요청을 바로 보낸다.

```text
method ∈ { GET, HEAD, POST }
Content-Type ∈ { application/x-www-form-urlencoded, multipart/form-data, text/plain }
커스텀 헤더 없음
```

### Preflight (사전 OPTIONS 요청)

위 조건 중 하나라도 어기면 브라우저는 **본 요청 전에 OPTIONS 요청**을 먼저 보낸다. 이 OPTIONS가 200을 받아야 본 요청을 보낸다.

현재 프로젝트의 모든 API는 다음 중 하나 이상이 어긋난다.

| API | 본 요청 method | Content-Type | 결과 |
|---|---|---|---|
| `POST /sessions` | POST | `application/json` | **Preflight** |
| `GET /posts` | GET | - | Simple (preflight 없음) |
| `POST /posts` | POST | `application/json` | **Preflight** |
| `PATCH /users` | PATCH | `application/json` | **Preflight** |
| `DELETE /sessions` | DELETE | `application/json` | **Preflight** |

PATCH, DELETE, 그리고 `Content-Type: application/json`이 붙는 모든 POST가 preflight를 유발한다. 즉 **이 프로젝트의 거의 모든 API는 preflight를 거친다**.

## B.4 Preflight 요청의 실제 모양

브라우저가 다음 OPTIONS 요청을 자동 전송한다.

```http
OPTIONS /sessions HTTP/1.1
Host: week12-be:8080
Origin: http://week12-fe:80
Access-Control-Request-Method: POST
Access-Control-Request-Headers: content-type
```

서버는 다음 헤더로 답해야 한다.

```http
HTTP/1.1 200 OK
Access-Control-Allow-Origin: http://week12-fe:80
Access-Control-Allow-Methods: POST
Access-Control-Allow-Headers: content-type
Access-Control-Allow-Credentials: true
```

이 응답이 200이 아니거나 `Allow-Origin`이 안 맞으면 브라우저는 본 요청을 보내지 않고 CORS 에러를 띄운다.

## B.5 `SecurityConfig`의 OPTIONS 처리

```java
.authorizeHttpRequests(auth -> auth
        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
        // ...
)
```

OPTIONS는 **인증 정보(쿠키, Authorization 헤더)를 안 보내는 사전 요청**이다. 그래도 Spring Security의 기본 인가는 인증을 요구할 수 있어 401을 돌려보낸다. 그래서 OPTIONS만 명시적으로 `permitAll()`로 뚫어 둔다.

nginx는 이 OPTIONS를 그대로 backend로 전달한다. 그러면 Spring Security가 `permitAll`로 200을 반환하고, 그 응답을 받은 브라우저가 본 요청을 이어서 보낸다.

## B.6 `WebConfig`의 CORS 매핑

파일: `config/WebConfig.java` (SecurityConfig와 짝을 이루는 Bean)

이 파일은 다음을 담당한다.

```java
@Override
public void addCorsMappings(CorsRegistry registry) {
    registry.addMapping("/**")
            .allowedOrigins(corsOriginProvider.getAllowedOrigins())
            .allowedMethods("GET", "POST", "PATCH", "DELETE", "OPTIONS")
            .allowedHeaders("*")
            .allowCredentials(true)
            .maxAge(3600);
}
```

- `allowedOrigins(...)`: `application.yaml`의 `cors.allowed-origins` 값을 그대로 받는다.
- `allowCredentials(true)`: `credentials: "include"`로 오는 요청을 허용한다. 이게 없으면 쿠키가 전송되지 않는다.
- `maxAge(3600)`: preflight 결과를 1시간 캐시한다. 매 요청마다 OPTIONS를 보내지 않게.

`SecurityConfig.cors(Customizer.withDefaults())`는 이 `WebConfig`의 CORS 설정을 Security 쪽에도 그대로 적용하라는 의미다. Spring Security도 자체 CORS 필터를 가지지만 `WebConfig` 설정이 우선이다.

## B.7 `credentials: "include"`가 만드는 추가 제약

프론트 `api.js`:

```js
const response = await fetch(`${API_BASE_URL}${endpoint}`, {
    ...options,
    headers,
    credentials: "include",
});
```

이건 `Cookie`와 `Authorization` 헤더를 cross-origin 요청에 포함하라는 뜻이다. **이걸 켜는 순간 브라우저는 다음과 같은 추가 제약을 적용한다.**

```text
Access-Control-Allow-Origin은 * 가 될 수 없다
→ 명시적인 origin 하나여야 한다
→ Vary: Origin 헤더를 응답에 포함해야 캐시 충돌을 막는다
```

그래서 `cors.allowed-origins`가 콤마로 여러 origin을 받지만 `WebConfig`는 `allowedOrigins(...)` 배열을 그대로 넘긴다. 단, **이 코드에서는 `allowCredentials(true)`와 함께 여러 origin을 허용하므로**, `CorsConfiguration`은 `setAllowedOrigins`로 직접 origin을 지정해야 한다. 콤마로 잘라서 여러 개를 넣어도 credentialed 요청에서는 origin이 정확히 일치해야 통과한다.

## B.8 HttpOnly / Secure / SameSite

5장에서 등장한 Refresh Cookie의 응답 헤더:

```java
return ResponseCookie.from(COOKIE_NAME, refreshToken)
        .httpOnly(true)
        .secure(refreshCookieSecure)
        .sameSite("Strict")
        .path(cookiePath)
        .maxAge(refreshExpirationSeconds)
        .build();
```

### HttpOnly

```text
JavaScript가 document.cookie로 쿠키를 읽을 수 없게 한다
→ XSS로 토큰 탈취 차단
```

`localStorage`에 Access Token을 두고 쿠키에 Refresh Token을 두는 패턴이 이 정책과 일치한다. Access Token은 JS가 직접 읽어서 `Authorization` 헤더에 붙여야 하므로 HttpOnly면 안 된다. Refresh Token은 JS가 읽을 필요 없이 매 요청에 자동 첨부되면 되므로 HttpOnly로 안전하게 가둔다.

### Secure

```text
HTTPS 연결에서만 쿠키를 전송
→ 평문 HTTP에서는 쿠키 자체가 안 감
```

`refreshCookieSecure`는 `application.yaml`의 `jwt.refresh-cookie-secure`로 결정된다.

```yaml
# local
jwt:
  refresh-cookie-secure: false    # HTTP localhost에서도 동작

# prod
jwt:
  refresh-cookie-secure: true     # HTTPS에서만
```

Compose `.env`가 비어 있으면 `JWT_REFRESH_COOKIE_SECURE: false`가 기본값이라 HTTP로도 쿠키가 간다. 운영 HTTPS 환경이라면 `.env`에서 `JWT_REFRESH_COOKIE_SECURE=true`로 명시해야 한다.

### SameSite

```text
쿠키가 다른 사이트에서 온 요청에 실리지 않게 제한
```

| 값 | 의미 |
|---|---|
| `Strict` | 다른 사이트로 발신되는 요청에는 쿠키 안 실림. 가장 엄격. |
| `Lax` | `<a href>`로 들어오는 GET 요청에는 실림. POST는 막힘. |
| `None` | 모든 cross-site 요청에 실림. 단 `Secure=true` 필수. |

현재 코드는 `Strict`. 즉:

- `http://fe-domain.com`에서 `http://be-domain.com`을 호출할 때, 사용자가 직접 fe-domain 페이지를 열어 둔 상태에서 발생한 요청에는 쿠키가 실린다.
- 외부 사이트의 `<img>`, `<iframe>`, `<form action>`을 통해 간접 호출되면 쿠키가 안 실린다.

이게 5장의 `SessionOriginInterceptor`와 함께 이중 방어선을 이룬다.

```text
SessionOriginInterceptor
→ 서버가 Origin 헤더로 사이트 출처를 직접 검증
→ CSRF defense-in-depth

SameSite=Strict
→ 브라우저가 cross-site 호출에서 쿠키 자체를 안 보냄
→ 1차 방어선
```

## B.9 nginx가 `Set-Cookie`를 그대로 전달하지 않는 이유

운영 nginx 설정 (12장 `backend-blue.conf`):

```nginx
location / {
    proxy_pass http://active_backend;
    proxy_set_header Host $host;
    proxy_set_header X-Real-IP $remote_addr;
    proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
    proxy_set_header X-Forwarded-Proto $scheme;
}
```

여기엔 `proxy_cookie_path`나 `proxy_cookie_domain`이 없다. 기본 동작은 **Spring이 만든 `Set-Cookie` 헤더를 그대로 브라우저로 전달**한다.

왜 그대로 전달해도 되나?

```text
Spring이 Set-Cookie에 SameSite=Strict + Path=/api/sessions를 붙여 보낸다
→ 브라우저는 이 쿠키를 정확히 /api/sessions 경로의 요청에만 붙여서 다시 보낸다
→ nginx는 그 쿠키를 받아서 backend로 전달할 책임만 진다
→ Set-Cookie는 응답 헤더라서 nginx가 가공할 일이 없다
```

만약 nginx가 같은 도메인의 다른 서비스에 backend를 두는 구조라면, `proxy_cookie_path`로 쿠키 경로를 재작성해 줘야 한다. 현재는 단일 도메인 + 단일 백엔드라 그 작업이 불필요하다.

## B.10 CORS preflight 흐름 종합

`POST /sessions` (로그인) 호출이 실제로 거치는 HTTP 왕복:

```text
[1] Browser: OPTIONS /sessions
    Host: be-domain
    Origin: fe-domain
    Access-Control-Request-Method: POST
    Access-Control-Request-Headers: content-type

[2] nginx: backend로 그대로 전달

[3] Spring Security: OPTIONS는 permitAll → 인가 통과
    Spring CORS: WebConfig 설정대로 응답 헤더 생성

[4] Browser: 200 OK, Access-Control-Allow-* 확인
    → 이제 본 요청을 보낸다고 결정

[5] Browser: POST /sessions
    Origin: fe-domain
    Content-Type: application/json
    { email, password }

[6] Spring Security: POST /sessions는 permitAll
    SecurityContext에 인증 없음 상태로 Controller 도달
    SessionService.login 실행

[7] Response: 200 OK
    Body: { accessToken: "..." }
    Set-Cookie: refreshToken=...; HttpOnly; SameSite=Strict; Path=/api/sessions; ...
```

## B.11 이해 확인

1. `POST /posts` 요청이 preflight를 거치는 이유를 method와 Content-Type으로 설명하라.
2. `SecurityConfig`에서 `OPTIONS`만 `permitAll`로 두는 이유는 무엇인가?
3. `allowCredentials(true)`일 때 `Access-Control-Allow-Origin: *`이 허용되지 않는 이유는 무엇인가?
4. Refresh Token을 `HttpOnly + SameSite=Strict` 쿠키로 두는 것이 `localStorage + Access Token` 조합보다 안전한 두 가지를 설명하라.
5. `SameSite=Strict`인 쿠키가 외부 `<iframe>` 안의 fetch 요청에 실리지 않는 이유는 무엇인가?
6. `cors.allowed-origins`가 여러 origin 콤마로 구분되어 있을 때 credentialed 요청에서 origin이 정확히 일치해야 하는 이유는 무엇인가?
