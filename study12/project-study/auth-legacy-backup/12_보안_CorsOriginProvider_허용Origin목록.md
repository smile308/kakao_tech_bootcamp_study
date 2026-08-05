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

이 설정 클래스는 Interceptor 객체를 직접 `new`하지 않습니다. 두 field를 생성자 매개변수로
받아 Spring이 이미 관리 중인 Bean을 사용합니다. 실제 실행 객체의 method는 요청이 들어올
때 실행되고, 이 클래스의 `addInterceptors`는 그 실행 규칙을 등록할 때만 실행됩니다.

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

## RequestLogInterceptor.java

### 파일 위치와 책임

- 실제 파일: `/Users/miles/Documents/GitHub/KTB4_Miles_Week12_Back/src/main/java/kr/adapterz/springdatajpa/config/RequestLogInterceptor.java`
- 책임: 모든 MVC 요청의 method·URI·응답 status·처리 시간을 로그로 남긴다.
- 등록 위치: `InterceptorConfig.addInterceptors`에서 `/**` pattern으로 등록된다.
- 상태 전달: 요청 시작 시 `HttpServletRequest` attribute에 시작 시각을 저장하고, 요청 완료 시 같은 attribute를 꺼내 elapsed time을 계산한다.
- 현재 사용하지 않는 값: `afterCompletion`의 `Exception ex` 매개변수는 현재 구현에서 로그에 포함하지 않는다.

### RequestLogInterceptor.java 전체 코드와 줄별 주석

```java
package kr.adapterz.springdatajpa.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Slf4j // Lombok이 이 클래스에 로거 field인 log를 생성한다.
@Component // Spring이 RequestLogInterceptor Bean을 생성한다.
public class RequestLogInterceptor implements HandlerInterceptor { // MVC 요청 전후 callback 계약을 구현한다.

    private static final String START_TIME = "startTime"; // request attribute에 시작 시각을 저장할 key다.

    @Override
    public boolean preHandle( // Controller 호출 전에 모든 요청에서 실행된다.
            HttpServletRequest request, // method와 URI를 읽고 request attribute를 저장한다.
            HttpServletResponse response, // 현재 응답 객체다. 이 method에서는 직접 변경하지 않는다.
            Object handler // 매핑된 handler 정보다. 현재 구현에서는 사용하지 않는다.
    ) {
        request.setAttribute(START_TIME, System.currentTimeMillis()); // 현재 시각을 이 요청의 attribute에 저장한다.

        log.info("[REQUEST] {} {}", request.getMethod(), request.getRequestURI()); // method와 URI를 로그 placeholder에 순서대로 넣는다.

        return true; // 로그 기록 후 다음 Interceptor와 Controller 처리를 계속한다.
    }

    @Override
    public void afterCompletion( // 요청 처리가 끝난 뒤 실행된다.
            HttpServletRequest request, // 시작 시각 attribute와 method·URI를 다시 읽는다.
            HttpServletResponse response, // 최종 HTTP status를 읽는다.
            Object handler, // 매핑된 handler 정보다. 현재 구현에서는 사용하지 않는다.
            Exception ex // 처리 중 발생한 예외다. 현재 구현에서는 사용하지 않는다.
    ) {
        Long startTime = (Long) request.getAttribute(START_TIME); // Object attribute를 Long으로 형변환해 시작 시각을 꺼낸다.
        long elapsedTime = System.currentTimeMillis() - startTime; // 현재 시각에서 시작 시각을 빼 처리 시간을 밀리초로 계산한다.

        log.info( // 응답 로그 한 줄을 기록한다.
                "[RESPONSE] {} {} status={} time={}ms", // {} placeholder가 아래 인자 순서대로 치환된다.
                request.getMethod(), // 첫 번째 {}: HTTP method다.
                request.getRequestURI(), // 두 번째 {}: 요청 URI다.
                response.getStatus(), // status={} 부분: 최종 응답 status다.
                elapsedTime // time={}ms 부분: 계산된 처리 시간이다.
        );
    }
}
```

### 코드 일부

```java
@Slf4j
@Component
public class RequestLogInterceptor implements HandlerInterceptor {

    private static final String START_TIME = "startTime";
```

`@Slf4j`는 Lombok annotation입니다. 실제 소스에 `Logger log = ...`를 작성하지 않아도
컴파일 시 `log` field를 만들어 주므로 아래 `log.info(...)`를 사용할 수 있습니다.

`START_TIME`은 요청 attribute의 key로 사용할 상수입니다. `static final`이므로 모든
Interceptor 객체가 같은 문자열을 공유하고, 실행 중 바뀌지 않습니다. 이 key 자체가 시간을
저장하는 것이 아니라, 아래 `request.setAttribute`와 `request.getAttribute`가 같은 값을
찾도록 하는 이름입니다.

### 코드 일부

```java
request.setAttribute(START_TIME, System.currentTimeMillis());

log.info("[REQUEST] {} {}", request.getMethod(), request.getRequestURI());

return true;
```

`HttpServletRequest`의 attribute는 현재 요청 객체에만 붙어 있는 key-value 저장 공간입니다.
여기서 시작 시각을 request에 저장하는 이유는 `preHandle`과 `afterCompletion`이 같은
Interceptor instance에서 실행되더라도, 요청별로 서로 다른 시간을 보존해야 하기 때문입니다.

`System.currentTimeMillis()`는 현재 시간을 밀리초 단위 숫자로 반환합니다. `log.info`의 `{}`는
SLF4J logging placeholder이고, 뒤의 매개변수가 왼쪽부터 각 placeholder에 들어갑니다. 따라서
첫 로그는 예를 들어 `[REQUEST] POST /sessions` 형태가 됩니다.

`return true`는 로그 Interceptor가 요청을 막지 않는다는 뜻입니다. 이 반환 뒤에
`SessionOriginInterceptor`, Security 처리, DispatcherServlet, Controller가 이어질 수 있습니다.

### 코드 일부

```java
public void afterCompletion(
        HttpServletRequest request,
        HttpServletResponse response,
        Object handler,
        Exception ex
) {
    Long startTime = (Long) request.getAttribute(START_TIME);
    long elapsedTime = System.currentTimeMillis() - startTime;
```

`afterCompletion`은 Controller 처리와 응답 완료 흐름이 끝난 뒤 Spring MVC가 호출합니다. 정상
응답뿐 아니라 Interceptor나 Controller에서 요청 처리가 중단된 뒤에도 완료 callback이 실행될
수 있습니다. `response.getStatus()`는 그 시점에 기록된 최종 status를 읽습니다.

`request.getAttribute`의 반환 타입은 `Object`이므로 `(Long)` 명시적 형변환이 필요합니다.
앞에서 `System.currentTimeMillis()`의 `long` 값을 저장했기 때문에 현재 코드에서는 Long으로
꺼낼 수 있습니다. `RequestLogInterceptor`가 `/**`에 등록되어 정상적으로 `preHandle`을
통과했다는 전제에서 `START_TIME`이 존재합니다.

`Exception ex`는 Spring이 완료 callback에 전달하는 예외 정보지만, 현재 구현에서는 변수만
받고 로그에 사용하지 않습니다. 따라서 현재 response 로그에는 예외 종류가 포함되지 않고,
status와 처리 시간만 기록됩니다.

### 코드 일부

```java
log.info(
        "[RESPONSE] {} {} status={} time={}ms",
        request.getMethod(),
        request.getRequestURI(),
        response.getStatus(),
        elapsedTime
);
```

이 로그의 데이터는 앞의 요청 로그와 `request` attribute를 통해 연결됩니다.

```text
preHandle
→ startTime 저장
→ [REQUEST] 로그
→ 다음 Interceptor·Controller·응답 처리
→ afterCompletion
→ startTime 조회
→ 현재 시각 - startTime
→ [RESPONSE] status/time 로그
```

Origin 검사가 실패해 Controller가 호출되지 않은 경우에도, 로그 Interceptor의 `preHandle`이
먼저 실행되었다면 `afterCompletion`에서 403 status를 기록할 수 있습니다. 반대로 Filter 단계에서
MVC Interceptor에 도달하기 전에 응답이 끝난 경우에는 이 MVC Interceptor callback 자체가
실행되지 않을 수 있습니다. 이 차이 때문에 Filter 로그와 MVC request log의 범위를 같다고
보면 안 됩니다.

---

## AuthSessionCleanupScheduler.java

### 파일 위치와 책임

- 실제 파일: `/Users/miles/Documents/GitHub/KTB4_Miles_Week12_Back/src/main/java/kr/adapterz/springdatajpa/service/AuthSessionCleanupScheduler.java`
- 책임: 만료된 `AuthSession` row를 주기적으로 삭제한다.
- 실행 주체: `SpringdatajpaApplication.java`의 `@EnableScheduling`으로 활성화된 Spring scheduler다. HTTP Controller가 이 method를 호출하지 않는다.
- 설정 출처: `jwt.refresh-session-cleanup-interval-millis` 값은 YAML에서 주입된다. 현재 공통 YAML은 기본 profile 값으로 3600000ms를 제공하고, Compose 환경변수로 재정의할 수 있다.
- 호출 대상: `AuthSessionRepository.deleteAllExpiredAtOrBefore(LocalDateTime.now())`.
- DB 변경: 현재 시각 이하의 `refreshExpiresAt`을 가진 세션 row를 bulk delete한다.
- 예외·transaction: repository의 DELETE query를 transaction 안에서 실행하기 위해 `@Transactional`을 붙인다.

### AuthSessionCleanupScheduler.java 전체 코드와 줄별 주석

```java
package kr.adapterz.springdatajpa.service;

import kr.adapterz.springdatajpa.repository.AuthSessionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Component // scheduler 객체를 Spring Bean으로 등록한다.
@RequiredArgsConstructor // AuthSessionRepository를 받는 생성자를 Lombok이 생성한다.
public class AuthSessionCleanupScheduler {

    private final AuthSessionRepository authSessionRepository; // 만료 세션 DELETE query를 제공하는 Repository다.

    @Scheduled( // Spring scheduler가 아래 method를 주기적으로 실행하도록 등록한다.
            initialDelayString = "${jwt.refresh-session-cleanup-interval-millis}", // 애플리케이션 시작 후 첫 실행까지 기다릴 시간이다.
            fixedDelayString = "${jwt.refresh-session-cleanup-interval-millis}" // 한 번의 실행이 끝난 뒤 다음 실행까지 기다릴 시간이다.
    )
    @Transactional // Repository DELETE query가 transaction 안에서 실행되도록 한다.
    public void deleteExpiredSessions() { // HTTP 요청과 독립적으로 만료 세션을 정리하는 scheduler method다.
        authSessionRepository.deleteAllExpiredAtOrBefore(LocalDateTime.now()); // 현재 시각 이하로 만료된 AuthSession row를 삭제한다.
    }
}
```

### 코드 일부

```java
@Component
@RequiredArgsConstructor
public class AuthSessionCleanupScheduler {

    private final AuthSessionRepository authSessionRepository;
```

이 클래스는 `service` package에 있지만 Controller가 사용하는 일반 업무 service가 아니라,
Spring scheduler가 정해진 시간에 호출하는 작업 Bean입니다. `AuthSessionRepository`는
앞서 설명한 `@Query DELETE` method를 제공하고, scheduler는 만료 기준 시각을 계산해 전달하는
역할만 담당합니다.

### 코드 일부

```java
@Scheduled(
        initialDelayString = "${jwt.refresh-session-cleanup-interval-millis}",
        fixedDelayString = "${jwt.refresh-session-cleanup-interval-millis}"
)
@Transactional
public void deleteExpiredSessions() {
```

`@Scheduled`는 Spring이 제공하는 scheduling annotation입니다. `@EnableScheduling`이 켜진
ApplicationContext에서 이 annotation을 찾으면 Spring이 별도의 scheduler 실행 흐름으로
method를 호출합니다.

- `initialDelayString`: 애플리케이션 시작 직후 바로 실행하지 않고 설정된 밀리초만큼 기다린 뒤 첫 실행합니다.
- `fixedDelayString`: 이전 실행이 끝난 시점부터 같은 설정 시간만큼 기다린 뒤 다음 실행합니다. 고정된 시각마다 실행하는 `fixedRate`와 다릅니다.
- 두 값이 같은 property를 사용하므로 현재 구현은 “시작 후 한 간격 대기 → 삭제 → 한 간격 대기 → 삭제” 흐름입니다.

`${jwt.refresh-session-cleanup-interval-millis}`는 Java method 호출 결과가 아니라 Spring이
annotation attribute를 해석할 때 YAML·환경변수 설정으로 치환하는 placeholder입니다. 현재
`application.yaml`의 기본값은 `3600000`이고, Compose의
`JWT_REFRESH_SESSION_CLEANUP_INTERVAL_MILLIS` 환경변수가 있으면 그 값이 Spring 설정으로
들어갑니다.

`@Transactional`은 scheduler method 실행 동안 transaction을 시작하고 method가 정상 종료되면
commit, 예외가 발생하면 rollback하는 Spring annotation입니다. Repository의 bulk DELETE query가
DB 변경 query이므로 transaction 경계가 필요합니다.

### 코드 일부

```java
public void deleteExpiredSessions() {
    authSessionRepository.deleteAllExpiredAtOrBefore(LocalDateTime.now());
}
```

`LocalDateTime.now()`는 scheduler가 실제 실행되는 순간의 서버 로컬 날짜·시간을 생성합니다.
그 값이 repository method의 `now` 매개변수로 전달됩니다.

Repository에서 실제로 실행되는 연결 코드는 다음과 같습니다.

```java
@Modifying(clearAutomatically = true, flushAutomatically = true)
@Query("""
        DELETE FROM AuthSession authSession
        WHERE authSession.refreshExpiresAt <= :now
        """)
int deleteAllExpiredAtOrBefore(@Param("now") LocalDateTime now);
```

여기서 `:now`가 scheduler가 전달한 현재 시각으로 치환됩니다. `refreshExpiresAt`이 현재 시각보다
과거이거나 같은 row가 삭제되고, 반환된 `int`는 삭제된 row 수입니다. Scheduler는 현재 그 반환값을
변수에 저장하거나 로그로 출력하지 않고 호출 결과만 사용합니다.

이 작업은 Refresh Token 자체를 브라우저에서 삭제하지 않습니다. 브라우저 Cookie 만료는
`SessionController.deleteSession`이 만료 Cookie를 응답 header로 보내는 흐름이고, 이 scheduler는
DB에 남아 있는 만료 `AuthSession` row를 나중에 제거하는 별도 정리 작업입니다.

## 이 문서 묶음의 최종 실행 흐름

```text
application-local.yaml / application-prod.yaml
├─ cors.allowed-origins
│  → CorsOriginProvider
│  ├─ WebConfig.corsConfigurer
│  │  → Spring MVC CORS mapping
│  └─ SessionOriginInterceptor.preHandle
│     → SessionOriginInterceptor.isCookieSessionRequest
│     → request Origin
│     → isAllowed(origin)
│        ├─ true  → Controller 계속
│        └─ false → ErrorResponseWriter 403 → Controller 중단
└─ jwt.refresh-session-cleanup-interval-millis
   → AuthSessionCleanupScheduler
   → LocalDateTime.now()
   → AuthSessionRepository.deleteAllExpiredAtOrBefore
   → 만료 AuthSession DELETE

모든 MVC 요청
→ RequestLogInterceptor.preHandle
→ 시작 시각 저장·REQUEST 로그
→ 다음 Interceptor·Controller 또는 차단 응답
→ RequestLogInterceptor.afterCompletion
→ 최종 status·elapsed time RESPONSE 로그
```

## 테스트 확인 범위

현재 `SecurityConfigTest.java`는 `SessionOriginInterceptor`가 포함된 Spring context와 MVC 흐름을
통해 다음을 확인합니다.

- 허용 Origin의 `POST /sessions`: Origin 검사를 통과하고 이후 요청 body 검증으로 이동한다.
- Origin이 없는 `POST /sessions`: `FORBIDDEN_ORIGIN`과 403을 반환한다.
- 허용되지 않은 Origin의 `POST /sessions`: 403을 반환한다.

현재 저장소에서 `AuthSessionCleanupScheduler`만을 대상으로 하는 별도 테스트 class는 검색되지
않았습니다. scheduler의 DELETE 실행 자체는 `AuthSessionRepository` query와 통합 테스트가
함께 검증하는지 다음 테스트 장에서 실제 파일을 대조해야 합니다.

## 다음 학습 묶음

이 보안 설정 묶음 다음에는 게시글·댓글 Entity를 읽습니다.
`Post.java` → `PostImage.java` → `PostCounter.java` → `PostViewCount.java` → `Like.java` →
`PostReport.java` → `Comment.java` 순서로 진행합니다.

현재 파일 진행률: **41개 확인 완료 / 최소 학습 대상 213개 = 약 19.2%**
