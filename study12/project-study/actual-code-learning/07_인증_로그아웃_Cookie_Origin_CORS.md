# 인증 흐름 4. 로그아웃·Cookie 만료·Origin/CORS

로그아웃 시 DB session revoke와 브라우저 Cookie 만료를 함께 처리하고, Cookie가 자동 전송되는 세션 endpoint를 Origin 정책으로 제한하는 흐름이다.


---

## SessionService.java — deleteSession 연결

전체 Service 원문은 흐름 1 문서에 있다.

### 3. `deleteSession` 로그아웃 흐름

호출 파일: `/Users/miles/Documents/GitHub/KTB4_Miles_Week12_Back/src/main/java/kr/adapterz/springdatajpa/controller/SessionController.java`

호출 메서드: `deleteSession`

1. Cookie가 null 또는 blank면 정상 종료한다.
2. 원문 Refresh Token을 hash로 바꾼다.
3. Repository가 hash로 AuthSession을 찾는다.
4. 찾은 Entity에 `revoke(LocalDateTime.now())`를 호출한다.
5. Entity field 변경은 transaction flush 시 DB의 `revoked_at`에 반영된다.

조회 결과가 없어도 `Optional.ifPresent` 때문에 예외 없이 종료한다. Cookie 만료 응답은 Service가 아니라 Controller가 `RefreshCookieProvider`로 만든다.

---

## SessionController.java — deleteSession 연결

전체 Controller 원문은 흐름 1 문서에 있다.

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

---

## RefreshCookieProvider.java — 만료 Cookie 연결

전체 Provider 원문은 흐름 3 문서에 있다.

### 7. createExpiredRefreshTokenCookie의 실행 흐름

`SessionController.deleteSession`이 호출한다. 같은 이름과 path의 Cookie에 Max-Age=0을 설정해 브라우저가 기존 Cookie를 삭제하도록 지시한다.

---

## CORS·Origin 설정과 MVC Interceptor

전체 원문은 다음 실제 파일들에 대해 이 문서 묶음의 해당 섹션으로 옮겼다.

# 4단계-9. CORS·Origin 검사·요청 로그·세션 정리

## 파일 위치와 이 문서 묶음을 지금 읽는 이유

- 실제 파일: `/Users/miles/Documents/GitHub/KTB4_Miles_Week12_Back/src/main/java/kr/adapterz/springdatajpa/config/CorsOriginProvider.java`
- 책임: YAML에서 허용 Origin 목록을 한 번 주입받아 정리하고, CORS 설정과 세션 Origin 검사에서 재사용할 수 있는 형태로 제공한다.
- 이 문서 묶음을 지금 읽는 이유: `CorsOriginProvider`가 만든 허용 목록이 `WebConfig`의 브라우저 CORS 설정과 `SessionOriginInterceptor`의 Cookie 요청 차단에 동시에 사용된다. `InterceptorConfig`는 두 Interceptor의 실행 범위를 등록하고, `RequestLogInterceptor`는 같은 MVC 요청의 시작·종료를 기록한다. 마지막 `AuthSessionCleanupScheduler`는 인증 세션 DB 정리를 요청 처리와 별도의 scheduler 흐름으로 실행한다.
- 호출하는 파일: `WebConfig.java`의 `corsConfigurer`가 `getAllowedOrigins()`를 호출한다. `SessionOriginInterceptor.java`의 `preHandle`가 `isAllowed(origin)`을 호출한다.
- 호출되는 파일·API: Spring이 `@Value`로 YAML 값을 전달한다. 내부에서 `Arrays.stream`, `String.split`, `String.trim`, `Collectors.toUnmodifiableSet`, `Set.toArray`를 사용한다.
- 외부에서 들어오는 값: `cors.allowed-origins` 설정 문자열. local에서는 YAML에 직접 적힌 comma-separated 문자열이고, prod에서는 `CORS_ALLOWED_ORIGINS` 환경변수가 YAML placeholder에 주입된다.
- 내부에서 생성하는 값: 공백과 빈 항목을 제거한 `Set<String> allowedOrigins`.
- 반환값 사용 위치: `getAllowedOrigins()`의 배열은 Spring MVC CORS 설정에, `isAllowed()`의 boolean은 세션 Cookie 요청의 통과·거부 판단에 사용된다.
- 예외·주의 지점: 설정 key가 없으면 `@Value` 주입 단계에서 애플리케이션 시작이 실패할 수 있다. 이 클래스는 Origin을 소문자화하거나 URL 경로를 제거하지 않으므로 문자열이 정확히 일치해야 한다.

## CorsOriginProvider.java 전체 코드와 줄별 주석

```java
package kr.adapterz.springdatajpa.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

@Component // Spring component scan이 이 클래스를 Bean으로 등록한다. 애플리케이션 시작 시 생성된다.
public class CorsOriginProvider {

    private final Set<String> allowedOrigins; // 허용할 Origin들을 저장한다. final은 참조를 생성자에서 한 번만 대입한다는 뜻이다.

    public CorsOriginProvider(
            @Value("${cors.allowed-origins}") String allowedOrigins // YAML의 cors.allowed-origins 값을 생성자 매개변수로 주입받는다.
    ) {
        this.allowedOrigins = Arrays.stream(allowedOrigins.split(",")) // comma-separated 설정 문자열을 쉼표 기준 여러 문자열로 나누고 Stream으로 처리한다.
                .map(String::trim) // 각 항목의 앞뒤 공백을 제거한다. String::trim은 각 String에 trim()을 실행하라는 method reference다.
                .filter(origin -> !origin.isEmpty()) // 빈 항목은 허용 목록에 넣지 않는다. 람다의 origin은 현재 Stream 항목이다.
                .collect(Collectors.toUnmodifiableSet()); // 결과를 수정할 수 없는 Set으로 모은다. 중복 Origin도 하나로 합쳐진다.
    }

    public String[] getAllowedOrigins() { // WebConfig가 Spring MVC CORS 설정에 넘길 배열을 요청할 때 호출한다.
        return allowedOrigins.toArray(String[]::new); // Set의 항목을 String[] 배열로 복사해 반환한다. 원래 Set 자체는 외부에 노출하지 않는다.
    }

    public boolean isAllowed(String origin) { // 요청의 Origin이 허용 목록에 있는지 검사할 때 SessionOriginInterceptor가 호출한다.
        return origin != null && allowedOrigins.contains(origin); // null은 거부하고, null이 아니면 Set의 완전 일치 여부를 boolean으로 반환한다.
    }
}
```

## 코드 일부

```java
@Component
public class CorsOriginProvider {

    private final Set<String> allowedOrigins;
```

`@Component`는 Java 문법이 아니라 Spring이 제공하는 stereotype annotation입니다. Spring Boot가
component scan을 수행할 때 이 클래스를 찾아 객체를 만들고 ApplicationContext에 Bean으로 등록합니다.
따라서 다른 설정 클래스가 `CorsOriginProvider`를 생성자로 요구하면 `new CorsOriginProvider(...)`를
직접 작성하지 않아도 Spring이 이미 만든 Bean을 전달합니다.

`private final Set<String>`에서 `Set<String>`은 문자열을 여러 개 저장하지만 중복을 허용하지 않는
컬렉션 타입입니다. `private`이므로 이 클래스 밖에서 field를 직접 바꿀 수 없고, `final`은 field가
가리키는 Set 참조를 생성자에서 한 번 정한 뒤 다른 Set으로 재대입하지 않는다는 의미입니다.
Set 내부를 수정할 수 없게 만드는 것은 아래의 `toUnmodifiableSet()` 호출이 담당합니다. 즉 `final`과
불변 Set은 서로 다른 개념입니다.

이 field를 별도로 저장하는 이유는 설정 문자열을 요청마다 다시 `split`하지 않고, 애플리케이션
시작 시 한 번 정리한 목록을 CORS 설정과 Origin 검사에서 함께 사용하기 위해서입니다.

## 코드 일부

```java
public CorsOriginProvider(
        @Value("${cors.allowed-origins}") String allowedOrigins
) {
```

이 생성자는 Spring이 Bean을 만들 때 호출하는 생성자입니다. 매개변수의 `@Value`는 Spring의
설정 주입 annotation이며, `${cors.allowed-origins}`라는 property key를 현재 활성 profile의
YAML에서 찾습니다.

현재 실제 설정 연결은 다음과 같습니다.

```yaml
# application-local.yaml
cors:
  allowed-origins: http://localhost:5500,http://127.0.0.1:5500,http://localhost:5173,http://127.0.0.1:5173
```

local에서는 위 문자열 전체가 생성자 매개변수 `allowedOrigins`에 들어옵니다.

```yaml
# application-prod.yaml
cors:
  allowed-origins: ${CORS_ALLOWED_ORIGINS}
```

prod에서는 Spring이 먼저 `CORS_ALLOWED_ORIGINS` 환경변수 값을 읽은 뒤, 그 결과를 같은 생성자
매개변수에 넣습니다. 이 placeholder에는 `:기본값`이 없으므로 prod 환경변수가 없으면 이 Bean을
정상적으로 만들 수 없습니다. 여기서 `allowedOrigins`라는 이름이 Java 매개변수와 YAML key의
이름이 같아서 연결되는 것이 아니라, `@Value` 안에 적힌 문자열 `${cors.allowed-origins}`가
연결 기준입니다.

## 코드 일부

```java
this.allowedOrigins = Arrays.stream(allowedOrigins.split(","))
        .map(String::trim)
        .filter(origin -> !origin.isEmpty())
        .collect(Collectors.toUnmodifiableSet());
```

이 부분은 생성자에 들어온 하나의 설정 문자열을 이후 검사에 사용할 Set으로 변환하는 순서입니다.

1. `allowedOrigins.split(",")`는 `http://a,http://b`를 `{"http://a", "http://b"}` 형태의
   문자열 배열로 나눕니다. `split`은 `String` 클래스가 제공하는 인스턴스 메서드입니다.
2. `Arrays.stream(...)`은 배열을 Stream으로 바꿔 다음 변환 작업을 순서대로 연결할 수 있게 합니다.
3. `.map(String::trim)`은 각 항목에 `trim()`을 적용합니다. 예를 들어 설정이
   `http://a, http://b`처럼 쉼표 뒤에 공백을 포함해도 두 번째 값은 `http://b`가 됩니다.
   `String::trim`은 `origin -> origin.trim()`을 짧게 쓴 method reference입니다.
4. `.filter(origin -> !origin.isEmpty())`는 빈 문자열을 제거합니다. `origin -> ...`는 현재
   Stream에서 하나씩 꺼낸 값을 받아 true인 항목만 다음 단계로 보내는 lambda입니다.
5. `.collect(Collectors.toUnmodifiableSet())`는 남은 항목을 수정 불가능한 Set으로 모읍니다.
   Set이므로 동일한 Origin이 여러 번 설정되어도 하나만 남습니다.

이 코드가 실행되는 시점은 HTTP 요청이 들어올 때가 아니라 `CorsOriginProvider` Bean을 만들 때인
애플리케이션 시작 시점입니다. 따라서 매 요청마다 문자열을 다시 파싱하지 않습니다.

## 코드 일부

```java
public String[] getAllowedOrigins() {
    return allowedOrigins.toArray(String[]::new);
}
```

이 메서드는 현재 다음 위치에서 호출됩니다.

```java
// /Users/miles/Documents/GitHub/KTB4_Miles_Week12_Back/src/main/java/kr/adapterz/springdatajpa/config/WebConfig.java
registry.addMapping("/**")
        .allowedOrigins(corsOriginProvider.getAllowedOrigins())
        .allowedMethods("GET", "POST", "PATCH", "DELETE", "OPTIONS")
        .allowedHeaders("*")
        .allowCredentials(true);
```

`WebConfig`는 Spring MVC에 CORS 규칙을 등록할 때 `getAllowedOrigins()`를 호출합니다.
`.allowedOrigins(...)`는 여러 문자열을 받는 Spring의 varargs API이므로, Provider가 보관 중인
Set을 `String[]`로 바꿔 전달합니다. 반환된 배열은 CORS 설정에 복사되어 사용되고, 이 메서드가
원본 Set 자체를 반환하지 않기 때문에 호출자가 Provider 내부 collection을 직접 변경할 수 없습니다.

여기서 CORS 설정과 Origin 차단은 같은 목록을 사용하지만 역할은 다릅니다.

- `WebConfig`의 CORS 설정: 브라우저의 교차 출처 요청에 대해 어떤 Origin·method·header·Cookie
  credential을 허용할지 Spring MVC에 등록합니다.
- `SessionOriginInterceptor`의 `isAllowed`: Cookie가 자동으로 전송되는 로그인·Refresh·로그아웃
  요청을 서버가 실제로 계속 처리할지 결정합니다.

따라서 `getAllowedOrigins()`가 배열을 반환한다고 해서 모든 요청을 허용하는 것이 아니며,
실제 세션 요청의 차단 여부는 다음 `isAllowed()` 호출에서 별도로 결정됩니다.

## 코드 일부

```java
public boolean isAllowed(String origin) {
    return origin != null && allowedOrigins.contains(origin);
}
```

이 메서드는 다음 위치에서 호출됩니다.

```java
// /Users/miles/Documents/GitHub/KTB4_Miles_Week12_Back/src/main/java/kr/adapterz/springdatajpa/config/SessionOriginInterceptor.java
String origin = request.getHeader("Origin");

if (corsOriginProvider.isAllowed(origin)) {
    return true;
}
```

`request.getHeader("Origin")`은 현재 HTTP 요청의 `Origin` header 값을 꺼냅니다. 예를 들어
브라우저가 `http://localhost:5173`에서 요청하면 그 문자열이 `origin` 매개변수로 전달됩니다.
header가 없으면 `origin`은 `null`입니다.

`origin != null && allowedOrigins.contains(origin)`에서 `&&`는 왼쪽 조건이 false이면 오른쪽을
평가하지 않는 short-circuit 논리 연산자입니다. 그래서 Origin이 없을 때 Set의 `contains`를
검사하기 전에 즉시 false가 되고, Origin이 있을 때만 Set에 동일한 문자열이 있는지 확인합니다.
이 메서드는 scheme·host·port가 모두 포함된 Origin 문자열을 완전 일치로 비교합니다. 예를 들어
`http://localhost:5173`과 `https://localhost:5173`, `http://localhost:5500`은 서로 다른 값입니다.
경로(`/posts`)나 query string까지 Origin에 포함해 비교하는 코드는 현재 없습니다.

`true`가 반환되면 `SessionOriginInterceptor.preHandle`이 true를 반환해 DispatcherServlet의
Controller 호출로 계속 진행할 수 있습니다. `false`가 반환되면 Interceptor가 403 응답을 쓰고
현재 요청을 Controller까지 보내지 않습니다. 이 클래스 자체는 HTTP status를 결정하지 않고,
허용 여부라는 boolean만 반환합니다.

## 코드 일부

```java
if (!isCookieSessionRequest(request)) {
    return true;
}

String origin = request.getHeader("Origin");

if (corsOriginProvider.isAllowed(origin)) {
    return true;
}

errorResponseWriter.write(response, HttpStatus.FORBIDDEN, ApiErrorCode.FORBIDDEN_ORIGIN);
return false;
```

위 코드는 `CorsOriginProvider`의 반환값이 실제 요청 흐름에서 사용되는 위치를 보여주기 위한
연결 코드입니다. `SessionOriginInterceptor`는 모든 요청에 Origin 검사를 하는 것이 아닙니다.
먼저 `/sessions`, `/sessions/refresh`의 POST와 `/sessions`의 DELETE처럼 Cookie session을
사용하는 요청인지 확인합니다. 그 대상이 아니면 `isAllowed`를 호출하지 않고 true를 반환합니다.

대상 요청이면 Origin header를 읽어 `isAllowed`에 전달합니다. 허용 목록에 있으면 true를
반환해 Controller까지 진행시키고, 없거나 null이면 `ErrorResponseWriter`로 403 JSON을 직접
작성한 뒤 false를 반환합니다. 따라서 Origin이 없는 요청을 허용하는 코드는 현재
`CorsOriginProvider`가 아니라, 이 Interceptor의 판단 결과에 따라 달라집니다. 현재 테스트는
Origin 없는 `POST /sessions`를 403으로 검증합니다.

## 현재 테스트와 확인 범위

실제 테스트 파일:
`/Users/miles/Documents/GitHub/KTB4_Miles_Week12_Back/src/test/java/kr/adapterz/springdatajpa/config/SecurityConfigTest.java`

- 허용된 Origin이 있는 `POST /sessions`: Origin 검사를 통과한 뒤 body 검증 단계의 400을 기대한다.
- Origin이 없는 `POST /sessions`: `FORBIDDEN_ORIGIN` JSON과 403을 기대한다.
- 허용되지 않은 Origin이 있는 `POST /sessions`: 403을 기대한다.

이 테스트는 `CorsOriginProvider`만 단독 호출하는 단위 테스트가 아니라, Spring Boot context와
Security filter, MVC interceptor를 함께 올려 세션 요청의 최종 결과를 확인합니다. 따라서
`isAllowed`의 반환값이 `SessionOriginInterceptor`와 응답 writer를 거쳐 HTTP status와 JSON으로
변환되는 경로를 검증합니다.

## 이 파일에서 확인한 핵심 흐름

```text
application-local.yaml / application-prod.yaml
→ Spring @Value("${cors.allowed-origins}")
→ CorsOriginProvider 생성자
→ split·trim·빈 항목 제거·불변 Set 생성
├─ WebConfig.corsConfigurer → getAllowedOrigins() → Spring MVC CORS 규칙
└─ SessionOriginInterceptor.preHandle
   → request Origin header
   → isAllowed(origin)
   ├─ true  → Controller 처리 계속
   └─ false → ErrorResponseWriter 403 → Controller 호출 중단
```

이제부터는 이 문서 안에서 관련 파일을 계속 설명합니다. 각 파일은 반드시 `전체 코드 → 코드 일부 → 바로 아래 설명` 순서로 분리합니다.

---

## WebConfig.java

### 파일 위치와 책임

- 실제 파일: `/Users/miles/Documents/GitHub/KTB4_Miles_Week12_Back/src/main/java/kr/adapterz/springdatajpa/config/WebConfig.java`
- 책임: Spring MVC 전체 경로에 CORS 규칙을 등록한다.
- 호출·생성 시점: Spring이 `@Configuration` Bean을 만들고 `@Bean corsConfigurer()`를 등록할 때 실행된다. HTTP 요청마다 이 메서드를 직접 호출하는 Controller는 없다.
- 호출 대상: `CorsOriginProvider.getAllowedOrigins()`를 호출해 허용 Origin 배열을 받는다.
- 반환값 사용 위치: 익명 `WebMvcConfigurer` 객체가 Spring MVC에 등록되고, 그 객체의 `addCorsMappings`가 CORS mapping을 구성한다.

### WebConfig.java 전체 코드와 줄별 주석

```java
package kr.adapterz.springdatajpa.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration // 이 클래스를 Spring 설정 Bean으로 등록하고 내부 @Bean 메서드를 실행하게 한다.
@RequiredArgsConstructor // final field를 매개변수로 받는 생성자를 Lombok이 생성한다.
public class WebConfig {

    private final CorsOriginProvider corsOriginProvider; // 앞에서 만든 허용 Origin 목록 Provider를 주입받는다.

    @Bean // 반환된 WebMvcConfigurer 객체를 Spring ApplicationContext의 Bean으로 등록한다.
    public WebMvcConfigurer corsConfigurer() { // Spring MVC의 CORS 규칙을 제공하는 설정 객체를 만든다.
        return new WebMvcConfigurer() { // interface의 필요한 default method만 익명 구현 객체로 재정의한다.
            @Override // WebMvcConfigurer에 선언된 addCorsMappings를 재정의한다.
            public void addCorsMappings(CorsRegistry registry) { // Spring MVC가 CORS mapping을 등록할 때 호출한다.
                registry.addMapping("/**") // 모든 Controller 경로에 이 CORS 규칙을 적용한다.
                        .allowedOrigins(corsOriginProvider.getAllowedOrigins()) // 설정에서 정리한 허용 Origin 배열을 전달한다.
                        .allowedMethods("GET", "POST", "PATCH", "DELETE", "OPTIONS") // 브라우저의 허용 HTTP method 목록을 지정한다.
                        .allowedHeaders("*") // 요청 header는 모든 이름을 허용한다.
                        .allowCredentials(true); // Cookie와 같은 credential을 교차 출처 요청에 포함할 수 있게 한다.
            }
        };
    }
}
```

### 코드 일부

```java
@Configuration
@RequiredArgsConstructor
public class WebConfig {

    private final CorsOriginProvider corsOriginProvider;
```

`@Configuration`은 Spring 설정을 담는 클래스임을 표시합니다. `@Component`처럼 component
scan 대상이 되지만, 내부의 `@Bean` 메서드가 반환하는 객체를 Spring Bean으로 관리한다는
의미가 추가됩니다.

`@RequiredArgsConstructor`는 Lombok이 다음과 같은 생성자를 컴파일 시 만들어 주도록 합니다.

```java
public WebConfig(CorsOriginProvider corsOriginProvider) {
    this.corsOriginProvider = corsOriginProvider;
}
```

실제 소스에 이 생성자를 직접 작성하지 않았지만, Spring은 이 생성자를 사용해 앞에서 등록한
`CorsOriginProvider` Bean을 전달합니다. `WebConfig`가 설정 문자열을 직접 읽지 않는 이유는
Origin 목록의 파싱 책임을 Provider 한 곳에 두고, Web MVC 설정은 이미 정리된 값을 사용하게
하기 위해서입니다.

### 코드 일부

```java
@Bean
public WebMvcConfigurer corsConfigurer() {
    return new WebMvcConfigurer() {
        @Override
        public void addCorsMappings(CorsRegistry registry) {
```

`@Bean` 메서드는 일반적인 업무 method처럼 Controller가 요청할 때 호출되는 것이 아닙니다.
Spring ApplicationContext를 구성하는 동안 호출되어 반환 객체를 Bean으로 등록합니다.

`WebMvcConfigurer`는 Spring MVC 설정을 확장하기 위한 interface입니다. `new WebMvcConfigurer() { ... }`
문법은 이름 없는 익명 구현 객체를 생성하는 Java 문법입니다. 이 객체 안에서 필요한
`addCorsMappings`만 `@Override`로 재정의하고, 나머지 default method는 Spring의 기본 구현을
그대로 사용합니다.

`CorsRegistry registry`는 Spring MVC가 CORS 규칙을 등록할 수 있도록 전달하는 설정 객체입니다.
`addCorsMappings`가 실행되는 동안 규칙을 구성하고, 그 결과가 이후 HTTP 요청 처리에 사용됩니다.

### 코드 일부

```java
registry.addMapping("/**")
        .allowedOrigins(corsOriginProvider.getAllowedOrigins())
        .allowedMethods("GET", "POST", "PATCH", "DELETE", "OPTIONS")
        .allowedHeaders("*")
        .allowCredentials(true);
```

각 호출은 같은 CORS mapping builder에 설정을 연쇄적으로 추가합니다.

- `addMapping("/**")`: 모든 URL 경로에 같은 규칙을 적용합니다.
- `allowedOrigins(...)`: `CorsOriginProvider`가 반환한 Origin과 일치하는 브라우저 출처를 허용합니다.
- `allowedMethods(...)`: 브라우저가 교차 출처로 보낼 수 있는 method 목록입니다. `OPTIONS`는 실제 업무 요청 전 브라우저가 보내는 preflight에 필요할 수 있습니다.
- `allowedHeaders("*")`: 요청 header 이름을 제한하지 않습니다. 이 설정은 아무 Origin이나 허용한다는 뜻이 아니라, 이미 `allowedOrigins`로 제한된 출처에서 어떤 header 이름을 보낼 수 있는지를 정하는 값입니다.
- `allowCredentials(true)`: Cookie와 Authorization 같은 credential을 교차 출처 요청에 포함하는 CORS 응답을 허용합니다. Cookie 기반 Refresh Token을 사용하기 때문에 이 설정이 필요합니다.

이 CORS mapping은 브라우저가 교차 출처 응답을 읽을 수 있는지와 preflight를 통과시키는지에
관여합니다. 그러나 서버가 Cookie 세션 endpoint를 업무 처리할지 여부를 직접 차단하는
코드는 `SessionOriginInterceptor`입니다. 따라서 WebConfig만으로 CSRF 방어가 완성되는 것은
아니며, 다음 Interceptor가 별도로 Origin을 검사합니다.

### WebConfig를 호출하는 주체와 호출이 연결되는 위치

`WebConfig`를 직접 호출하는 Controller나 Service는 현재 코드에 없습니다. 호출 경로는
애플리케이션 시작 코드와 Spring Boot·Spring MVC의 자동 설정을 통해 연결됩니다.

```text
SpringdatajpaApplication.main
→ SpringApplication.run(SpringdatajpaApplication.class, args)
→ @SpringBootApplication의 component scan
→ config.WebConfig 발견
→ @Configuration Bean 등록
→ @Bean corsConfigurer() 실행
→ WebMvcConfigurer Bean 등록
→ Spring Boot MVC 자동 설정이 WebMvcConfigurer 목록에 포함
→ Spring MVC 설정 단계에서 addCorsMappings(CorsRegistry) 호출
```

프로젝트에서 이 연결의 시작점은 다음 두 곳입니다.

```java
// SpringdatajpaApplication.java
@SpringBootApplication
public class SpringdatajpaApplication {
    public static void main(String[] args) {
        SpringApplication.run(SpringdatajpaApplication.class, args);
    }
}
```

`@SpringBootApplication`에는 component scan 기능이 포함되어 있습니다. 기본 scan 기준
package는 이 annotation이 붙은 `kr.adapterz.springdatajpa`이고, `WebConfig`의 package인
`kr.adapterz.springdatajpa.config`는 그 하위 package입니다. 그래서 개발자가
`new WebConfig(...)`를 작성하거나 `webConfig.corsConfigurer()`를 직접 호출하지 않아도
Spring이 `WebConfig`를 발견합니다.

```java
// WebConfig.java
@Configuration
public class WebConfig {
    @Bean
    public WebMvcConfigurer corsConfigurer() {
        return new WebMvcConfigurer() {
            @Override
            public void addCorsMappings(CorsRegistry registry) {
                // CORS 규칙 등록
            }
        };
    }
}
```

`@Configuration`은 이 클래스를 Spring 설정 Bean으로 등록하라는 표시이고, `@Bean`은
`corsConfigurer()`의 반환 객체를 ApplicationContext의 Bean으로 등록하라는 표시입니다.
따라서 `corsConfigurer()` 자체는 HTTP 요청마다 호출되는 업무 method가 아니라 Bean 생성
과정에서 실행됩니다. 반환된 익명 객체의 실제 타입은 `WebMvcConfigurer`입니다.

프로젝트의 `build.gradle`에는 `spring-boot-starter-web`이 선언되어 있습니다. 이 의존성으로
Spring MVC와 Spring Boot의 MVC 자동 설정이 포함됩니다. 자동 설정은 ApplicationContext에서
`WebMvcConfigurer` 타입의 Bean들을 찾아 MVC 설정 callback에 연결합니다. 내부 동작을
단순화하면 다음과 같은 의미입니다.

```java
List<WebMvcConfigurer> configurers = applicationContext
        .getBeansOfType(WebMvcConfigurer.class);

for (WebMvcConfigurer configurer : configurers) {
    configurer.addCorsMappings(registry);
}
```

위 코드는 프로젝트에 직접 작성된 코드가 아니라 Spring MVC 내부 동작을 이해하기 위한
축약 표현입니다. Spring Boot의 MVC 자동 설정과 Spring MVC의 `WebMvcConfigurer` 위임
구조가 이 역할을 담당합니다. 그러므로 “호출을 어디에 명시했는가?”에 대한 답은
`WebConfig`를 호출하는 한 줄이 application source에 있는 것이 아니라,
`@SpringBootApplication`의 component scan, `@Configuration`·`@Bean`, 그리고
`spring-boot-starter-web`이 제공하는 자동 설정에 선언적으로 연결되어 있다는 것입니다.

실행 시점도 세 단계로 나누어야 합니다.

1. `main()`의 `SpringApplication.run()`이 ApplicationContext 생성을 시작합니다.
2. Spring이 `WebConfig`를 발견하고 `corsConfigurer()`를 실행해
   `WebMvcConfigurer` Bean을 준비합니다.
3. Spring MVC 설정 단계에서 `addCorsMappings()`를 한 번 호출해
   `CorsRegistry`에 `/**`, 허용 Origin, method, header, credential 규칙을 등록합니다.

그 뒤 실제 HTTP 요청이 들어오면 `addCorsMappings()`를 다시 호출하는 것이 아닙니다.
시작 시 등록된 CORS 설정을 Spring MVC의 요청 처리 인프라가 사용해 Origin과 preflight를
판단하고 response header를 구성합니다. 반면 `SessionOriginInterceptor.preHandle()`은
요청마다 실행되어 Cookie 세션 endpoint의 Origin을 별도로 검사합니다. 즉 다음 두 경로는
같은 `CorsOriginProvider` 목록을 사용하지만 실행 시점과 책임이 다릅니다.

```text
애플리케이션 시작
→ WebConfig.addCorsMappings
→ Spring MVC CORS 규칙 준비

각 HTTP 요청
→ 준비된 CORS 규칙 적용
→ SessionOriginInterceptor.preHandle
→ 필요하면 Controller 실행 또는 403 직접 응답
```

---

## SessionOriginInterceptor.java

### 파일 위치와 책임

- 실제 파일: `/Users/miles/Documents/GitHub/KTB4_Miles_Week12_Back/src/main/java/kr/adapterz/springdatajpa/config/SessionOriginInterceptor.java`
- 책임: Cookie가 자동 전송되는 로그인·Refresh·로그아웃 요청의 Origin을 검사한다.
- 호출 파일: `InterceptorConfig.addInterceptors`가 `/sessions`, `/sessions/refresh` 경로에 이 Interceptor를 등록한다.
- 호출 대상: `CorsOriginProvider.isAllowed(origin)`, `ErrorResponseWriter.write(...)`.
- 외부 입력: `HttpServletRequest`의 HTTP method, request URI, `Origin` header.
- 반환값: `preHandle`의 `true`는 다음 Interceptor/Controller로 진행, `false`는 현재 요청을 Controller로 보내지 않음을 뜻한다.
- 예외: `ErrorResponseWriter.write`가 Servlet response에 쓰는 과정에서 `IOException`이 발생할 수 있어 `preHandle`이 `throws IOException`을 선언한다.

### `preHandle` 호출·인자·반환값의 실제 연결

`SessionOriginInterceptor`의 `preHandle()`을 프로젝트의 Controller나 Service가 직접 호출하지는
않습니다. 먼저 `InterceptorConfig`가 이 Interceptor Bean을 Spring MVC의 registry에 등록합니다.

```java
// InterceptorConfig.java
registry.addInterceptor(sessionOriginInterceptor)
        .addPathPatterns("/sessions", "/sessions/refresh");
```

`sessionOriginInterceptor` field에는 `@Component`로 등록된
`SessionOriginInterceptor` Bean이 생성자 주입됩니다. `addPathPatterns`는 URL 경로를 기준으로
등록하므로 `DELETE /sessions`도 `/sessions`에 해당합니다. 다만 실제 method까지 세밀하게
제한하는 것은 `SessionOriginInterceptor.isCookieSessionRequest()`입니다.

HTTP 요청이 들어오면 Spring MVC의 `DispatcherServlet`이 URL에 대응하는 Controller handler를
찾고, 내부 요청 처리 체인에 등록된 `HandlerInterceptor`들의 `preHandle()`을 실행합니다.
개념적인 호출 형태는 다음과 같습니다.

```java
boolean proceed = sessionOriginInterceptor.preHandle(
        request,
        response,
        handler
);
```

위 호출 코드는 프로젝트에 직접 작성된 것이 아니라 Spring MVC 내부의 요청 처리 흐름을
나타낸 축약 표현입니다. 실제로는 Spring MVC가 `HandlerExecutionChain`에 등록된
Interceptor를 순서대로 실행합니다.

세 매개변수의 값은 다음 주체가 제공합니다.

- `request`: Servlet container가 현재 HTTP 요청마다 만든 `HttpServletRequest`입니다. Spring MVC가 같은 객체를 전달하며, Interceptor는 `getMethod()`, `getRequestURI()`, `getHeader("Origin")`으로 method·경로·Origin header를 읽습니다.
- `response`: Servlet container가 만든 현재 `HttpServletResponse`입니다. Interceptor가 거부할 때 status·content type·JSON body를 직접 기록할 수 있도록 Spring MVC가 전달합니다.
- `handler`: 현재 URL에 매핑된 Controller method 정보입니다. 보통 Spring MVC의 `HandlerMethod`가 들어오지만, 현재 구현에서는 실제로 사용하지 않습니다.

현재 요청에서 값이 전달되는 순서는 다음과 같습니다.

```text
HTTP 요청
→ Servlet container가 request·response 생성
→ DispatcherServlet이 Controller handler 탐색
→ Spring MVC가 preHandle(request, response, handler) 호출
→ isCookieSessionRequest(request)
→ request.getHeader("Origin")
→ corsOriginProvider.isAllowed(origin)
```

`preHandle()`의 반환값은 호출한 Controller가 받는 반환값이 아닙니다. Spring MVC의 요청 처리
체인이 이 boolean을 읽어 다음 진행 여부를 결정합니다.

```text
true
→ 다음 Interceptor 또는 Controller 실행

false
→ 현재 Interceptor chain 중단
→ Controller 호출하지 않음
```

허용되지 않은 Origin일 때는 `false`만 반환하는 것이 아니라, 먼저 다음 코드가 응답을 완성합니다.

```java
errorResponseWriter.write(
        response,
        HttpStatus.FORBIDDEN,
        ApiErrorCode.FORBIDDEN_ORIGIN
);
return false;
```

`ErrorResponseWriter.write()`는 전달받은 `response`에 403 status, JSON content type, error
body를 기록합니다. 그 다음 `false`는 Spring MVC에 Controller를 호출하지 말라고 알립니다.
따라서 반환값의 사용 주체는 `SessionController`가 아니라 Spring MVC입니다.

허용 Origin이거나 Cookie 세션 대상이 아닌 요청이면 `true`가 반환됩니다. 이 `true`는
“사용자 인증 성공”을 의미하지 않고, “이 Interceptor가 요청을 차단하지 않으므로 다음 단계로
진행하라”는 뜻입니다. 세션 endpoint는 이후 Controller로 가서 `SessionService`를 호출하고,
보호된 다른 endpoint는 별도의 Security 인가 규칙도 통과해야 합니다.

### SessionOriginInterceptor.java 전체 코드와 줄별 주석

```java
package kr.adapterz.springdatajpa.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import kr.adapterz.springdatajpa.exception.ApiErrorCode;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.io.IOException;

@Component // Spring이 이 Interceptor를 Bean으로 등록한다.
@RequiredArgsConstructor // final field인 Provider와 response writer를 받는 생성자를 Lombok이 만든다.
public class SessionOriginInterceptor implements HandlerInterceptor { // Spring MVC의 Controller 전후 처리 계약을 구현한다.

    private final CorsOriginProvider corsOriginProvider; // 허용 Origin인지 판단할 Provider다.
    private final ErrorResponseWriter errorResponseWriter; // 거부 시 403 JSON을 직접 작성할 Bean이다.

    @Override
    public boolean preHandle( // DispatcherServlet이 Controller를 호출하기 전에 실행하는 Interceptor method다.
            HttpServletRequest request, // 현재 HTTP 요청의 method, URI, header를 제공한다.
            HttpServletResponse response, // 거부 시 status와 JSON body를 기록할 응답 객체다.
            Object handler // 현재 요청에 매핑된 Controller handler 정보다. 이 구현에서는 직접 사용하지 않는다.
    ) throws IOException { // response writer의 Servlet I/O 예외를 호출 흐름 밖으로 전달할 수 있음을 선언한다.
        if (!isCookieSessionRequest(request)) { // Cookie 세션을 사용하는 대상 endpoint가 아니면 Origin 검사를 적용하지 않는다.
            return true; // 다음 Interceptor와 Controller로 요청을 계속 진행한다.
        }

        String origin = request.getHeader("Origin"); // 요청 header에서 Origin 값을 꺼낸다. 없으면 null이다.

        if (corsOriginProvider.isAllowed(origin)) { // null이 아니고 Provider의 허용 Set과 정확히 일치하는지 검사한다.
            return true; // 허용 Origin이면 Controller 처리를 계속하게 한다.
        }

        errorResponseWriter.write(response, org.springframework.http.HttpStatus.FORBIDDEN, ApiErrorCode.FORBIDDEN_ORIGIN); // 허용되지 않은 요청에 403 JSON을 직접 기록한다.
        return false; // DispatcherServlet이 Controller를 호출하지 않도록 현재 Interceptor chain을 중단한다.
    }

    private boolean isCookieSessionRequest(HttpServletRequest request) { // Origin 검사를 적용할 method와 path인지 판별한다.
        String method = request.getMethod(); // GET·POST·DELETE 같은 HTTP method를 읽는다.
        String path = request.getRequestURI(); // query string을 제외한 요청 URI를 읽는다.

        return method.equals("POST") && path.equals("/sessions") // 로그인 요청이면 true다.
                || method.equals("POST") && path.equals("/sessions/refresh") // Refresh Token 재발급 요청이면 true다.
                || method.equals("DELETE") && path.equals("/sessions"); // 로그아웃 요청이면 true다.
    }
}
```

### 코드 일부

```java
@Component
@RequiredArgsConstructor
public class SessionOriginInterceptor implements HandlerInterceptor {

    private final CorsOriginProvider corsOriginProvider;
    private final ErrorResponseWriter errorResponseWriter;
```

`HandlerInterceptor`는 Spring MVC가 Controller를 호출하기 전·후에 코드를 실행할 수 있게
제공하는 Spring interface입니다. 이 클래스는 Filter보다 뒤쪽의 MVC 요청 단계에서 동작하며,
`preHandle`에서 `false`를 반환하면 Controller 호출 전에 처리를 멈출 수 있습니다.

`CorsOriginProvider`는 허용 목록을 제공하고, `ErrorResponseWriter`는 거부 응답을 작성합니다.
Interceptor가 설정 parsing이나 JSON serialization을 직접 하지 않는 이유는 각각의 책임을
기존 Bean에 위임하기 위해서입니다.

### 코드 일부

```java
public boolean preHandle(
        HttpServletRequest request,
        HttpServletResponse response,
        Object handler
) throws IOException {
```

Spring MVC가 이 method를 자동으로 호출합니다. Controller가 직접 `preHandle()`을 부르는 것이
아닙니다.

- `request`: 현재 요청에서 method·URI·header를 읽는 객체입니다.
- `response`: 오류를 직접 끝낼 때 status·content type·body를 기록하는 객체입니다.
- `handler`: 현재 URL에 연결된 Controller method 정보입니다. 현재 구현은 매개변수를 받지만 사용하지 않습니다.
- `throws IOException`: 내부에서 `HttpServletResponse.getWriter()`를 사용하는 response writer가 I/O 예외를 던질 수 있으므로, 이 Interceptor method가 그 checked exception을 선언합니다.

### 코드 일부

```java
if (!isCookieSessionRequest(request)) {
    return true;
}

String origin = request.getHeader("Origin");

if (corsOriginProvider.isAllowed(origin)) {
    return true;
}
```

요청 흐름은 다음과 같습니다.

1. 현재 method/path가 Cookie 세션 대상인지 확인합니다.
2. 대상이 아니면 `isAllowed`를 호출하지 않고 true를 반환합니다. 게시글 조회처럼 Access Token만 사용하는 요청을 이 Origin 정책으로 막지 않습니다.
3. 대상이면 `Origin` header를 읽습니다. header가 없으면 `null`이 됩니다.
4. Provider가 true를 반환하면 Controller까지 진행합니다.
5. Provider가 false를 반환하면 아래 오류 응답 경로로 이동합니다.

여기서 `return true`는 “인증 성공”이라는 뜻이 아니라, 이 Interceptor가 요청 처리를 막지
않는다는 뜻입니다. 뒤의 Security Filter, 다른 Interceptor, DispatcherServlet과 Controller가
계속 실행될 수 있습니다.

### 코드 일부

```java
errorResponseWriter.write(
        response,
        org.springframework.http.HttpStatus.FORBIDDEN,
        ApiErrorCode.FORBIDDEN_ORIGIN
);
return false;
```

`ErrorResponseWriter`는 Controller의 `GlobalExceptionHandler`가 처리할 예외를 던지는 대신,
Interceptor 단계에서 `HttpServletResponse`에 403 status와 `FORBIDDEN_ORIGIN` JSON을 직접
기록합니다. 그 다음 `return false`가 MVC에 “이 요청의 Controller 처리를 진행하지 말라”고
알립니다.

즉, `false`만 반환해서 응답 body가 자동으로 만들어지는 것이 아닙니다. 먼저 `write`가 response에
status·content type·JSON body를 기록하고, `false`는 그 뒤의 Controller 호출을 막는 역할을
합니다. `GlobalExceptionHandler`는 Controller 호출 중 전파된 예외를 처리하는 경로이므로,
이 Interceptor가 직접 응답을 끝내는 경우에는 그 Handler까지 도달하지 않습니다.

### 코드 일부

```java
private boolean isCookieSessionRequest(HttpServletRequest request) {
    String method = request.getMethod();
    String path = request.getRequestURI();

    return method.equals("POST") && path.equals("/sessions")
            || method.equals("POST") && path.equals("/sessions/refresh")
            || method.equals("DELETE") && path.equals("/sessions");
}
```

이 메서드는 세 가지 조합만 true로 반환합니다.

- `POST /sessions`: 로그인
- `POST /sessions/refresh`: Refresh Token 재발급
- `DELETE /sessions`: 로그아웃

Java에서 `&&`가 `||`보다 우선 계산되므로 각 줄은 `(method 조건 && path 조건)` 한 묶음으로
해석됩니다. 괄호를 작성하지 않았지만 다음과 같은 의미입니다.

```java
(method.equals("POST") && path.equals("/sessions"))
|| (method.equals("POST") && path.equals("/sessions/refresh"))
|| (method.equals("DELETE") && path.equals("/sessions"));
```

현재 구현은 `OPTIONS` 요청을 이 메서드에서 Cookie session request로 분류하지 않습니다.
따라서 CORS preflight 자체는 이 Interceptor의 Origin 차단 조건에 걸리지 않고, WebConfig의
CORS 규칙이 처리할 수 있습니다. 반면 실제 POST·DELETE 세션 요청은 Origin이 없거나 허용
목록과 다르면 403으로 차단됩니다.

---


## InterceptorConfig.java

### 파일 위치와 책임

- 실제 파일: `/Users/miles/Documents/GitHub/KTB4_Miles_Week12_Back/src/main/java/kr/adapterz/springdatajpa/config/InterceptorConfig.java`
- 책임: Spring MVC Interceptor registry에 요청 로그 Interceptor와 세션 Origin Interceptor를 등록하고 적용 경로를 정한다.
- 실행 시점: 애플리케이션 시작 중 Spring MVC 설정을 구성할 때 `addInterceptors`가 호출된다.
- 호출 대상: `RequestLogInterceptor`와 `SessionOriginInterceptor` Bean을 registry에 등록한다.
- 요청 시 효과: 로그 Interceptor는 모든 경로, Origin Interceptor는 `/sessions`와 `/sessions/refresh` 경로에만 적용된다.

### InterceptorConfig.java 전체 코드와 줄별 주석

```java
package kr.adapterz.springdatajpa.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration // Spring MVC 설정을 제공하는 Configuration Bean으로 등록한다.
@RequiredArgsConstructor // 두 final Interceptor Bean을 받는 생성자를 Lombok이 만든다.
public class InterceptorConfig implements WebMvcConfigurer { // MVC 설정 확장 지점을 구현한다.

    private final RequestLogInterceptor requestLogInterceptor; // 모든 요청의 시작·종료를 기록하는 Interceptor다.
    private final SessionOriginInterceptor sessionOriginInterceptor; // Cookie 세션 요청의 Origin을 검사하는 Interceptor다.

    @Override
    public void addInterceptors(InterceptorRegistry registry) { // Spring MVC가 Interceptor 등록을 위해 시작 시 호출한다.
        registry.addInterceptor(requestLogInterceptor) // 로그 Interceptor를 registry에 추가한다.
                .addPathPatterns("/**"); // 모든 URL 경로에 로그 Interceptor를 적용한다.

        registry.addInterceptor(sessionOriginInterceptor) // Origin 검사 Interceptor를 registry에 추가한다.
                .addPathPatterns("/sessions", "/sessions/refresh"); // 세션 관련 두 URL 경로에만 적용한다.
    }
}
```

### 코드 일부

```java
@Configuration
@RequiredArgsConstructor
public class InterceptorConfig implements WebMvcConfigurer {

    private final RequestLogInterceptor requestLogInterceptor;
    private final SessionOriginInterceptor sessionOriginInterceptor;
```

### 두 `private final` field의 의미와 값의 출처

```java
private final RequestLogInterceptor requestLogInterceptor;
private final SessionOriginInterceptor sessionOriginInterceptor;
```

두 줄은 메서드를 호출하는 코드가 아니라 `InterceptorConfig` 객체가 사용할 의존성을
저장하는 instance field 선언입니다.

- `RequestLogInterceptor`: field의 타입입니다. 요청 시작·완료 로그를 기록하는 객체를 담습니다.
- `requestLogInterceptor`: 그 객체를 가리키는 field 이름입니다.
- `SessionOriginInterceptor`: Cookie 세션 요청의 Origin을 검사하는 객체 타입입니다.
- `sessionOriginInterceptor`: 그 객체를 가리키는 field 이름입니다.
- `private`: `InterceptorConfig` 내부에서만 이 field를 직접 사용할 수 있습니다.
- `final`: 생성자에서 한 번 참조를 대입한 뒤 다른 Interceptor 객체로 바꿀 수 없습니다. `final`이 객체 내부의 모든 상태를 불변으로 만든다는 뜻은 아닙니다.

현재 클래스에는 이 field에 값을 대입하는 생성자가 직접 보이지 않습니다. 위의
`@RequiredArgsConstructor`가 Lombok을 통해 다음과 같은 생성자를 컴파일 시 만들어 줍니다.

```java
public InterceptorConfig(
        RequestLogInterceptor requestLogInterceptor,
        SessionOriginInterceptor sessionOriginInterceptor
) {
    this.requestLogInterceptor = requestLogInterceptor;
    this.sessionOriginInterceptor = sessionOriginInterceptor;
}
```

Spring은 `@Configuration`인 `InterceptorConfig`를 Bean으로 만들 때 이 생성자를 사용합니다.
`RequestLogInterceptor`와 `SessionOriginInterceptor`는 각각 `@Component`로 등록되어 있으므로,
Spring이 이미 만든 두 Bean을 생성자 매개변수에 넣어 줍니다. 따라서 이 클래스에서 직접
`new RequestLogInterceptor()` 또는 `new SessionOriginInterceptor()`를 작성하지 않습니다.

두 field에 주입된 객체는 다음 코드에서 실제로 사용됩니다.

```java
registry.addInterceptor(requestLogInterceptor)
        .addPathPatterns("/**");

registry.addInterceptor(sessionOriginInterceptor)
        .addPathPatterns("/sessions", "/sessions/refresh");
```

여기서 두 객체의 `preHandle()`을 직접 호출하는 것이 아닙니다. `InterceptorRegistry`에
“어떤 Interceptor Bean을 어떤 URL pattern에 등록할지”를 전달합니다. 이후 HTTP 요청이
들어오면 Spring MVC가 registry에 등록된 객체를 꺼내 `RequestLogInterceptor.preHandle()`과
`SessionOriginInterceptor.preHandle()`을 실행합니다.

이 설정 클래스는 Interceptor 객체를 직접 `new`하지 않습니다. 두 field를 생성자 매개변수로
받아 Spring이 이미 관리 중인 Bean을 사용합니다. 실제 실행 객체의 method는 요청이 들어올
때 실행되고, 이 클래스의 `addInterceptors`는 그 실행 규칙을 등록할 때만 실행됩니다.

### `implements WebMvcConfigurer`의 의미

```java
public class InterceptorConfig implements WebMvcConfigurer {
```

`WebMvcConfigurer`는 Spring MVC가 제공하는 설정용 interface입니다. `implements`는
`InterceptorConfig`가 이 interface를 구현하겠다는 뜻이며, 이 타입으로 사용할 수 있는
메서드들을 재정의할 수 있다는 약속입니다.

여기서는 `addInterceptors(InterceptorRegistry registry)`를 `@Override`로 재정의했습니다.
그래서 Spring MVC가 `InterceptorConfig` Bean을 `WebMvcConfigurer`로 인식하고, 애플리케이션
시작 중 MVC 설정을 만들 때 이 callback을 호출할 수 있습니다.

```text
@Configuration으로 InterceptorConfig Bean 등록
→ WebMvcConfigurer 타입의 설정 객체로 인식
→ Spring MVC가 addInterceptors(registry) 호출
→ registry에 두 Interceptor와 URL pattern 등록
→ 이후 요청마다 등록된 pattern에 맞는 preHandle 실행
```

`implements` 자체가 메서드를 자동 실행하는 것은 아닙니다. interface를 구현하지 않으면
`InterceptorConfig`가 `WebMvcConfigurer`의 설정 callback으로 전달될 수 없고, Spring MVC가
이 클래스의 `addInterceptors()`를 설정 callback으로 사용할 근거도 없어집니다.

`WebConfig`와 비교하면 차이가 더 분명합니다. `WebConfig` 클래스 자체는
`implements WebMvcConfigurer`를 적지 않고, `@Bean` 메서드가 `WebMvcConfigurer`를 구현한
익명 객체를 반환합니다. 반면 `InterceptorConfig`는 클래스 자체가 interface를 구현하므로
클래스의 `addInterceptors()`가 callback이 됩니다.

```java
// InterceptorConfig: 클래스 자체가 interface 구현
public class InterceptorConfig implements WebMvcConfigurer {
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // Spring MVC가 설정 시 호출
    }
}

// WebConfig: @Bean이 interface 구현 객체 반환
@Bean
public WebMvcConfigurer corsConfigurer() {
    return new WebMvcConfigurer() {
        @Override
        public void addCorsMappings(CorsRegistry registry) {
            // Spring MVC가 설정 시 호출
        }
    };
}
```

### 코드 일부

```java
@Override
public void addInterceptors(InterceptorRegistry registry) {
    registry.addInterceptor(requestLogInterceptor)
            .addPathPatterns("/**");

    registry.addInterceptor(sessionOriginInterceptor)
            .addPathPatterns("/sessions", "/sessions/refresh");
}
```

`registry.addInterceptor(...).addPathPatterns(...)`는 registry에 Interceptor와 URL pattern을
연결하는 builder 형태의 호출입니다.

- `requestLogInterceptor`는 `/**`이므로 게시글·사용자·세션을 포함한 모든 MVC 요청에 적용됩니다.
- `sessionOriginInterceptor`는 `/sessions`와 `/sessions/refresh`에만 적용됩니다. `DELETE /sessions`도 URL path가 `/sessions`이므로 이 등록 대상에 포함되고, 실제 method 제한은 Interceptor 내부의 `isCookieSessionRequest`가 확인합니다.

등록 순서는 로그 Interceptor가 먼저, Origin Interceptor가 다음입니다. 세션 요청에서는
로그 `preHandle`이 먼저 시작 시간을 기록한 뒤 Origin 검사가 실행됩니다. Origin에서 거부되면
Controller에는 도달하지 않지만, 이미 실행된 로그 Interceptor의 completion callback에서는
최종 403 응답을 기록할 수 있습니다.

---
