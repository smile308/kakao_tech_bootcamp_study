# 인증 흐름 2. Access Token 요청 인증

로그인 때 발급된 Access Token이 이후 보호 endpoint 요청에서 어떻게 검증되고 SecurityContext에 저장되는지 설명한다.

실제 실행 흐름:

```text
api.js Authorization header
→ SecurityFilterChain
→ JwtAuthenticationFilter
→ JwtProvider.getAccessTokenClaims
→ CustomUserDetailsService.loadUserByUserId
→ UsernamePasswordAuthenticationToken
→ SecurityContextHolder
→ authorizeHttpRequests → Controller
```


---

## AccessTokenClaims.java와 JwtProvider 검증 연결

- 실제 파일: `/Users/miles/Documents/GitHub/KTB4_Miles_Week12_Back/src/main/java/kr/adapterz/springdatajpa/auth/AccessTokenClaims.java`

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

---

## SecurityConfig.java

- 실제 파일: `/Users/miles/Documents/GitHub/KTB4_Miles_Week12_Back/src/main/java/kr/adapterz/springdatajpa/config/SecurityConfig.java`

파일: `/Users/miles/Documents/GitHub/KTB4_Miles_Week12_Back/src/main/java/kr/adapterz/springdatajpa/config/SecurityConfig.java`

## 이 파일의 역할과 사용 위치

`SecurityConfig`는 애플리케이션의 HTTP 보안 규칙을 Spring Security에 등록하는 설정 파일이다. Controller가 직접 호출하는 업무 class가 아니라, 애플리케이션 시작 시 Spring이 읽고 Security Filter Chain과 인증 관련 Bean을 생성하는 구성 코드다.

이 파일에서 만든 Bean과 사용 위치는 다음과 같다.

| Bean | 이 Bean의 역할 | 실제 사용 위치 |
|---|---|---|
| `SecurityFilterChain` | 요청마다 CORS·CSRF·인증·인가 규칙을 실행 | 모든 HTTP 요청이 Controller에 도착하기 전 |
| `AuthenticationManager` | 로그인 email/password 검증을 수행 | `SessionService.createSession` |
| `PasswordEncoder` | 비밀번호 hash 생성과 입력값 비교 | `UserService`, Spring Security 인증 과정 |

`JwtAuthenticationFilter`와 `ErrorResponseWriter`는 이 설정에서 주입받아 Security Filter Chain에 연결한다. 따라서 이 파일을 이해하려면 “메서드를 누가 호출하는가”보다 “애플리케이션 시작 때 Bean을 만들고, 이후 모든 요청에 규칙을 적용한다”는 실행 시점을 먼저 알아야 한다.

```java
package kr.adapterz.springdatajpa.config;

import kr.adapterz.springdatajpa.auth.JwtAuthenticationFilter;
import kr.adapterz.springdatajpa.exception.ApiErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/*
 * 1. 보안 설정 Bean
 * @Configuration은 이 class가 Bean 생성 설정을 제공한다는 뜻이다.
 * @EnableWebSecurity는 Spring Security의 web filter 기능을 활성화한다.
 * @RequiredArgsConstructor가 JwtAuthenticationFilter와 ErrorResponseWriter를 생성자로 받게 한다.
 */
@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter; // Authorization header를 검사하는 Filter Bean이다.
    private final ErrorResponseWriter errorResponseWriter; // Filter 밖에서 발생한 401·403 응답 JSON을 작성한다.

    /*
     * 2. SecurityFilterChain
     * HTTP 요청이 Controller에 도착하기 전에 Spring Security가 적용할 규칙을 만든다.
     * 반환된 chain은 Spring Security filter chain에 등록된다.
     */
    @Bean // 반환 객체를 Spring Bean으로 등록한다.
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception { // HttpSecurity builder로 보안 규칙을 조립한다.
        http
                /*
                 * 3. CORS
                 * CorsOriginProvider/WebConfig의 CORS 설정을 Security chain에서도 사용한다.
                 * Customizer.withDefaults()는 별도 옵션 없이 Spring 기본 구성을 적용한다.
                 */
                .cors(Customizer.withDefaults())

                /*
                 * 4. CSRF
                 * Access Token은 Authorization header, Refresh Token은 SameSite Cookie를 사용한다.
                 * 현재 구현은 CSRF 기능을 비활성화하고 Origin 검사와 Cookie 정책을 별도로 적용한다.
                 */
                .csrf(AbstractHttpConfigurer::disable)

                /*
                 * 5. 서버 세션 정책
                 * Spring Security의 서버 세션을 만들지 않고 매 요청의 JWT로 인증한다.
                 */
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                )

                /*
                 * 6. 인증·인가 실패 응답
                 * 인증되지 않은 요청은 401, 인증은 됐지만 권한이 없으면 403이다.
                 * ErrorResponseWriter가 code/message/status JSON을 직접 response에 기록한다.
                 */
                .exceptionHandling(exception -> exception
                        .authenticationEntryPoint((request, response, authException) -> { // 인증 정보가 없을 때 실행되는 lambda다.
                            errorResponseWriter.write(response, org.springframework.http.HttpStatus.UNAUTHORIZED, ApiErrorCode.UNAUTHORIZED); // 401 JSON을 response에 쓴다.
                        })
                        .accessDeniedHandler((request, response, accessDeniedException) -> { // 권한 부족 시 실행되는 lambda다.
                            errorResponseWriter.write(response, org.springframework.http.HttpStatus.FORBIDDEN, ApiErrorCode.FORBIDDEN); // 403 JSON을 response에 쓴다.
                        })
                )

                /*
                 * 7. endpoint 접근 규칙
                 * permitAll은 JWT 인증 없이 통과시킨다.
                 * anyRequest().authenticated()는 위에서 허용하지 않은 모든 요청에 인증을 요구한다.
                 */
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll() // 브라우저 CORS preflight 요청을 허용한다.

                        .requestMatchers(HttpMethod.GET, "/actuator/health").permitAll() // 배포 health check가 인증 없이 상태를 확인한다.

                        .requestMatchers(HttpMethod.POST, "/users").permitAll() // 회원가입은 로그인 전에도 가능해야 한다.

                        .requestMatchers( // 로그인과 Refresh는 Access Token이 없을 수 있으므로 허용한다.
                                HttpMethod.POST,
                                "/sessions",
                                "/sessions/refresh"
                        ).permitAll()

                        .requestMatchers(HttpMethod.DELETE, "/sessions").permitAll() // 로그아웃은 Cookie가 없어도 정리 요청을 받을 수 있다.

                        .anyRequest().authenticated() // 나머지 endpoint는 SecurityContext의 인증이 필요하다.
                )

                /*
                 * 8. JWT Filter 위치
                 * UsernamePasswordAuthenticationFilter보다 앞에서 JWT를 읽는다.
                 * Filter가 인증 성공 시 SecurityContext를 채우므로 뒤의 authorize 규칙이 authenticated를 확인할 수 있다.
                 */
                .addFilterBefore(
                        jwtAuthenticationFilter,
                        UsernamePasswordAuthenticationFilter.class
                );

        return http.build(); // 조립한 설정을 실제 SecurityFilterChain 객체로 만들어 반환한다.
    }

    /*
     * 9. AuthenticationManager Bean
     * SessionService가 로그인 email/password 검증을 위임할 객체다.
     * AuthenticationConfiguration이 UserDetailsService·PasswordEncoder와 연결된 Manager를 제공한다.
     */
    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration configuration) throws Exception {
        return configuration.getAuthenticationManager(); // Spring이 구성한 AuthenticationManager를 반환한다.
    }

    /*
     * 10. PasswordEncoder Bean
     * 회원가입 시 저장한 비밀번호와 로그인 입력 비밀번호 비교에 사용된다.
     * BCrypt는 원문을 복호화하는 것이 아니라 matches로 hash와 입력을 비교한다.
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(); // BCrypt 방식 PasswordEncoder 객체를 Bean으로 등록한다.
    }
}
```

## 코드 실행 흐름

```text
HTTP 요청
→ CORS·CSRF·session policy 적용
→ JwtAuthenticationFilter 실행
→ authorizeHttpRequests 경로 규칙 확인
→ permitAll이면 Controller로 이동
→ authenticated이면 SecurityContext 인증 확인
→ 실패 시 ErrorResponseWriter로 401·403 JSON 작성
```

## 각 Bean이 실제로 사용되는 순간

### `securityFilterChain`

애플리케이션 시작 시 Spring이 이 `@Bean` 메서드를 호출한다. `HttpSecurity`에 설정을 쌓은 뒤 `http.build()`가 반환한 `SecurityFilterChain`을 Spring Security가 모든 HTTP 요청의 앞단에 등록한다. 그래서 Controller가 실행되기 전에 endpoint 접근 허용 여부와 인증 상태가 확인된다.

### `authenticationManager`

이 Bean은 Controller가 직접 호출하지 않는다. `SessionService`가 로그인 요청을 처리할 때 다음 코드를 실행한다.

```java
authenticationManager.authenticate(
        new UsernamePasswordAuthenticationToken(
                request.getEmail(),
                request.getPassword()
        )
);
```

`AuthenticationManager`는 등록된 `UserDetailsService`로 사용자를 찾고 `PasswordEncoder`로 비밀번호를 비교한 뒤, 성공하면 `Authentication`을 반환한다.

### `passwordEncoder`

회원가입에서는 다음처럼 비밀번호를 hash로 만든다.

```java
passwordEncoder.encode(request.getPassword())
```

로그인에서는 같은 원문 비밀번호를 다시 저장하는 것이 아니라, Spring Security가 저장된 hash와 입력값을 `matches` 방식으로 비교한다. 따라서 `PasswordEncoder` Bean은 회원가입 저장과 로그인 검증 양쪽에서 사용된다.

### `jwtAuthenticationFilter` 연결

다음 설정으로 Filter가 기본 UsernamePassword 인증 Filter보다 앞에 배치된다.

```java
.addFilterBefore(
        jwtAuthenticationFilter,
        UsernamePasswordAuthenticationFilter.class
)
```

Filter는 Authorization header를 읽어 JWT를 검증하고, 성공하면 `SecurityContext`에 인증 객체를 저장한다. 그 뒤 `anyRequest().authenticated()`가 이 인증 객체를 확인한다.

## 의존성 연결 위치

- `JwtAuthenticationFilter`: `/auth/JwtAuthenticationFilter.java`에서 Authorization header를 검증하고 SecurityContext를 만든다.
- `ErrorResponseWriter`: Filter·Interceptor·Security exception handler가 같은 `code/message/status` JSON 형식을 사용하도록 한다.
- `AuthenticationManager`: `SessionService.createSession`이 email/password 검증을 호출한다.
- `PasswordEncoder`: `UserService` 회원가입의 password encode와 Spring Security 인증 비교에 사용된다.
- `/actuator/health`: `application.yaml`의 Actuator 노출 설정과 배포 health check에서 연결된다.

## JwtAuthenticationFilter.java

- 실제 파일: `/Users/miles/Documents/GitHub/KTB4_Miles_Week12_Back/src/main/java/kr/adapterz/springdatajpa/auth/JwtAuthenticationFilter.java`

## JwtAuthenticationFilter.java

파일 경로:

/Users/miles/Documents/GitHub/KTB4_Miles_Week12_Back/src/main/java/kr/adapterz/springdatajpa/auth/JwtAuthenticationFilter.java

파일의 책임:

- Authorization header에서 Bearer Access Token을 읽습니다.
- JwtProvider에 token 검증을 위임합니다.
- 검증된 userId로 현재 User를 다시 조회합니다.
- Authentication을 SecurityContext에 저장합니다.
- 인증 실패 시 Controller까지 보내지 않고 401 JSON 응답을 작성합니다.

### JwtAuthenticationFilter.java 전체 코드

~~~java
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

@Component // Spring이 이 class를 Bean으로 등록한다.
@RequiredArgsConstructor // final field 세 개를 받는 생성자를 Lombok이 만든다.
public class JwtAuthenticationFilter extends OncePerRequestFilter { // Spring이 제공하는 요청 Filter 구조를 상속한다.

    private final JwtProvider jwtProvider; // JWT 서명과 claims를 검증한다.
    private final CustomUserDetailsService customUserDetailsService; // token userId로 DB User를 조회한다.
    private final ErrorResponseWriter errorResponseWriter; // Filter 단계에서 오류 JSON을 작성한다.

    @Override // 부모 Filter의 제외 여부 hook을 재정의한다.
    protected boolean shouldNotFilter(HttpServletRequest request) { // 현재 요청에 JWT Filter를 적용하지 않을지 반환한다.
        String method = request.getMethod(); // HTTP method를 읽는다.
        String path = request.getServletPath(); // 현재 요청의 servlet path를 읽는다.

        return method.equals("OPTIONS") // CORS preflight는 인증 없이 통과시킨다.
                || method.equals("POST") && path.equals("/users") // 회원가입은 로그인 전 요청이므로 제외한다.
                || method.equals("POST") && path.equals("/sessions") // 로그인은 Access Token이 없으므로 제외한다.
                || method.equals("POST") && path.equals("/sessions/refresh") // Refresh Cookie를 사용하므로 제외한다.
                || method.equals("DELETE") && path.equals("/sessions"); // 로그아웃은 Cookie로 처리하므로 제외한다.
    }

    @Override // 부모 Filter의 실제 요청 처리 hook을 재정의한다.
    protected void doFilterInternal( // 인증 후 다음 Filter로 넘기거나 오류 응답을 끝낸다.
            HttpServletRequest request, // 현재 HTTP 요청 객체다.
            HttpServletResponse response, // 현재 HTTP 응답 객체다.
            FilterChain filterChain // 다음 Filter로 넘기는 Servlet API 객체다.
    ) throws ServletException, IOException { // Filter와 Servlet 처리 중 checked exception을 선언한다.

        String authorizationHeader = request.getHeader("Authorization"); // Authorization header 전체를 읽는다.

        if (authorizationHeader == null || !authorizationHeader.startsWith("Bearer ")) { // header가 없거나 Bearer 형식이 아니면 token 검증을 하지 않는다.
            filterChain.doFilter(request, response); // 다음 Filter와 Controller로 요청을 계속 전달한다.
            return; // 아래 JWT 검증 코드를 실행하지 않는다.
        }

        try { // token 검증과 User 조회 실패를 하나의 catch로 처리한다.
            String token = authorizationHeader.substring(7); // Bearer와 공백 7글자를 제거하고 JWT만 남긴다.
            AccessTokenClaims tokenClaims = jwtProvider.getAccessTokenClaims(token); // 검증된 userId와 authVersion을 받는다.

            CustomUserDetails userDetails = // DB User를 Security용 wrapper로 받는다.
                    customUserDetailsService.loadUserByUserId(tokenClaims.userId()); // 검증된 userId로 User를 조회한다.

            if (!userDetails.isEnabled() // 삭제·정지 User는 인증할 수 없다.
                    || userDetails.getAuthVersion() != tokenClaims.authVersion()) { // DB version과 token version이 다르면 오래된 token이다.
                throw new DataNullException("No_User"); // 아래 catch가 처리할 예외를 만든다.
            }

            /* Spring Security가 제공하는 Authentication 구현 class다.
             * 로그인에서는 email/password 입력 객체로 사용하고,
             * 이 Filter에서는 이미 검증된 JWT와 User를 인증 결과로 저장하는 객체로 사용한다.
             */
            UsernamePasswordAuthenticationToken authentication = // 현재 요청의 인증 결과를 담을 객체다.
                    new UsernamePasswordAuthenticationToken( // Spring Security Authentication을 생성한다.
                            userDetails, // principal 위치에 현재 사용자를 저장한다.
                            null, // JWT 검증이 끝났으므로 password credential은 저장하지 않는다.
                            userDetails.getAuthorities() // 사용자의 권한 목록을 저장한다.
                    );

            /* WebAuthenticationDetailsSource는 Spring Security가 제공한다.
             * 현재 request에서 remote address와 session 정보 같은 부가 detail을 만들어
             * principal·권한과 별도로 authentication 객체에 저장한다.
             */
            authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request)); // request detail을 authentication 객체에 저장한다.

            /* SecurityContextHolder는 Spring Security가 현재 실행 요청의 인증 정보를 보관하는 진입점이다.
             * 여기서 저장해야 뒤의 Security Filter와 authenticated 규칙이 이 요청을 인증된 요청으로 판단한다.
             */
            SecurityContextHolder.getContext().setAuthentication(authentication); // 현재 요청의 인증 결과를 SecurityContext에 저장한다.

            /* FilterChain은 Jakarta Servlet이 제공하고 실행 시 Spring이 전달한다.
             * doFilter를 호출해야 다음 Filter와 DispatcherServlet·Controller로 요청이 이동한다.
             */
            filterChain.doFilter(request, response); // 다음 Filter와 Controller로 요청을 계속 전달한다.

        } catch (AuthException | DataNullException e) { // JWT 검증·User 조회·상태 확인 예외를 함께 받는다.
            /* multi-catch: 두 예외가 발생하면 같은 실패 흐름으로 들어온다.
             * e는 현재 코드에서 직접 읽지 않지만 catch 대상 예외를 가리키는 변수다.
             */
            SecurityContextHolder.clearContext(); // 실패한 인증 정보가 남지 않게 현재 context를 비운다.

            /* ErrorResponseWriter는 Controller 밖의 Filter에서 직접 호출한다.
             * response에 401 status와 INVALID_TOKEN JSON을 기록하고,
             * filterChain을 더 진행하지 않아 Controller 실행을 막는다.
             */
            errorResponseWriter.write(response, org.springframework.http.HttpStatus.UNAUTHORIZED, ApiErrorCode.INVALID_TOKEN); // 401 JSON 응답을 직접 작성한다.
        }
    }
}
~~~

### 이 파일의 코드 일부

~~~java
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtProvider jwtProvider;
    private final CustomUserDetailsService customUserDetailsService;
    private final ErrorResponseWriter errorResponseWriter;
~~~

이 코드의 설명:

Component는 Spring이 이 class를 Bean으로 생성해 ApplicationContext에 등록하게 합니다. Bean이 만들어지는 시점은 애플리케이션 시작 시점이고, JWT 검증이 실행되는 시점은 HTTP 요청이 Filter Chain에 들어온 시점입니다.

RequiredArgsConstructor는 세 final field를 받는 생성자를 Lombok이 생성하게 합니다. Spring은 JwtProvider, CustomUserDetailsService, ErrorResponseWriter Bean을 생성자에 주입합니다.

OncePerRequestFilter는 Spring이 제공하는 부모 class입니다. 이 class를 상속하면 Spring의 Filter 실행 구조 안에서 shouldNotFilter와 doFilterInternal을 구현할 수 있습니다.

### 이 파일의 코드 일부

~~~java
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String method = request.getMethod();
        String path = request.getServletPath();

        return method.equals("OPTIONS")
                || method.equals("POST") && path.equals("/users")
                || method.equals("POST") && path.equals("/sessions")
                || method.equals("POST") && path.equals("/sessions/refresh")
                || method.equals("DELETE") && path.equals("/sessions");
    }
~~~

이 코드의 설명:

Spring Security가 이 method를 호출하고 boolean 결과를 사용합니다. true이면 이 Filter의 JWT 처리 구간을 건너뛰고, false이면 doFilterInternal로 진행합니다.

request.getMethod는 OPTIONS, GET, POST, DELETE 같은 HTTP method를 읽습니다. request.getServletPath는 현재 요청 경로를 읽습니다.

- OPTIONS: CORS preflight이므로 제외합니다.
- POST /users: 회원가입은 Access Token 발급 전이므로 제외합니다.
- POST /sessions: 로그인은 Access Token이 없으므로 제외합니다.
- POST /sessions/refresh: Access Token이 아니라 Refresh Cookie를 사용하므로 제외합니다.
- DELETE /sessions: Cookie를 사용해 로그아웃하므로 제외합니다.

Filter를 건너뛴다는 사실과 endpoint가 공개라는 사실은 다릅니다. 실제 공개·보호 여부는 SecurityConfig의 permitAll과 authenticated 규칙이 결정합니다.

### 이 파일의 코드 일부

~~~java
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
~~~

이 코드의 설명:

doFilterInternal은 Controller가 직접 호출하지 않습니다. Spring Security Filter Chain이 요청마다 호출합니다.

request는 Authorization header를 읽을 객체이고, response는 Filter가 오류 status와 JSON을 기록할 객체입니다.

FilterChain은 사용자가 선언한 class가 아닙니다. import된 Jakarta Servlet API의 interface이며, 실제 객체는 Spring과 Servlet 실행 환경이 이 method를 호출할 때 전달합니다.

filterChain.doFilter(request, response)를 호출하면 현재 Filter 다음의 Filter로 이동합니다. 모든 Filter가 끝나면 DispatcherServlet이 Controller를 호출합니다. 이 메서드를 호출하지 않으면 요청이 더 진행되지 않습니다.

### 이 파일의 코드 일부

~~~java
String authorizationHeader = request.getHeader("Authorization");

if (authorizationHeader == null || !authorizationHeader.startsWith("Bearer ")) {
    filterChain.doFilter(request, response);
    return;
}
~~~

이 코드의 설명:

getHeader는 Authorization header를 읽습니다. header가 없으면 null을 반환합니다.

authorizationHeader가 null인지 먼저 검사하는 이유는 null인 상태에서 startsWith를 호출하면 오류가 발생하기 때문입니다. ||는 왼쪽 조건이 참이면 오른쪽을 평가하지 않는 short-circuit OR입니다.

Bearer로 시작하지 않는 header도 이 Filter에서는 JWT 검증하지 않습니다. 이 경우 chain을 계속 진행하고, 보호된 endpoint라면 뒤의 SecurityConfig가 인증 부족 여부를 판단합니다.

여기서 “chain을 계속 진행한다”는 것은 이 Filter가 인증을 성공시킨다는 뜻이 아닙니다. 현재 Filter가 인증하지 않은 상태로 다음 Filter에게 request와 response를 넘긴다는 뜻입니다.

Filter Chain은 다음처럼 여러 Filter가 연결된 호출 구조입니다.

~~~text
현재 JwtAuthenticationFilter
→ filterChain.doFilter(request, response)
→ 다음 Security Filter
→ 다음 Security Filter
→ Authorization 검사
→ DispatcherServlet
→ Controller
~~~

이 요청에는 Authorization header가 없거나 Bearer 형식이 아니므로 이 Filter가 SecurityContext에 Authentication을 저장하지 않습니다. 따라서 공개 endpoint는 permitAll 규칙에 따라 계속 진행할 수 있지만, 보호된 endpoint는 뒤의 인증·인가 단계에서 현재 인증 정보가 없다는 사실을 확인하고 401 응답을 만들게 됩니다.

`filterChain.doFilter(request, response)`는 Controller를 바로 호출하는 코드가 아닙니다. 현재 Filter 뒤에 연결된 다음 Filter를 호출하고, 그 Filter가 다시 자신의 다음 Filter를 호출합니다. 모든 Filter가 끝나면 DispatcherServlet이 URL에 맞는 Controller를 호출합니다.

`return`은 현재 doFilterInternal method만 종료합니다. 실행 순서는 다음과 같습니다.

~~~text
filterChain.doFilter(request, response) 호출
→ 다음 Filter와 이후 처리 실행
→ downstream 처리가 끝나 이 Filter로 돌아옴
→ return 실행
→ 현재 doFilterInternal 종료
~~~

즉 `return`이 이미 호출된 filterChain의 다음 처리를 취소하는 것은 아닙니다. 오히려 return이 필요한 이유는 chain이 끝난 뒤 현재 method가 아래 JWT 검증 코드로 계속 내려가지 않게 하기 위해서입니다. return이 없으면 Bearer가 아닌 값에 substring(7)을 시도하는 등 잘못된 후속 처리가 실행될 수 있습니다.

### 이 파일의 코드 일부

~~~java
String token = authorizationHeader.substring(7);
AccessTokenClaims tokenClaims = jwtProvider.getAccessTokenClaims(token);

CustomUserDetails userDetails =
        customUserDetailsService.loadUserByUserId(tokenClaims.userId());
~~~

이 코드의 설명:

Bearer와 공백은 7글자이므로 substring(7)이 JWT 부분만 남깁니다. substring은 문자열을 자르는 method이고, token 서명 검증은 하지 않습니다.

getAccessTokenClaims는 JwtProvider에 선언된 method입니다. 이 method가 서명·만료·claims를 검증하고 AccessTokenClaims를 반환합니다.

tokenClaims.userId()는 request body나 사용자가 직접 입력한 값이 아니라 검증된 JWT claim에서 나온 userId입니다.

왼쪽의 CustomUserDetails는 변수에 저장할 객체 타입이고, userDetails는 변수 이름입니다. 오른쪽의 customUserDetailsService는 생성자 주입받은 Service Bean입니다. loadUserByUserId는 userId를 받아 UserRepository에서 User를 조회한 뒤 new CustomUserDetails(user)를 반환합니다.

따라서 전체 값 이동은 다음과 같습니다.

~~~text
검증된 JWT
→ tokenClaims.userId()
→ loadUserByUserId(userId)
→ UserRepository 조회
→ User Entity를 CustomUserDetails로 감쌈
→ 반환된 wrapper를 userDetails에 저장
~~~

### 이 파일의 코드 일부

~~~java
if (!userDetails.isEnabled()
        || userDetails.getAuthVersion() != tokenClaims.authVersion()) {
    throw new DataNullException("No_User");
}
~~~

이 코드의 설명:

isEnabled가 false이면 User가 삭제되었거나 정지된 상태입니다.

getAuthVersion은 현재 DB User의 version이고 tokenClaims.authVersion은 JWT가 발급될 때 저장된 version입니다. 두 값이 다르면 이전 token으로 판단합니다.

둘 중 하나라도 문제가 있으면 DataNullException을 던집니다. 이 예외는 아래 catch로 이동해 외부에는 INVALID_TOKEN 401로 응답됩니다.

### 이 파일의 코드 일부

~~~java
UsernamePasswordAuthenticationToken authentication =
        new UsernamePasswordAuthenticationToken(
                userDetails,
                null,
                userDetails.getAuthorities()
        );
~~~

이 코드의 설명:

UsernamePasswordAuthenticationToken은 Spring Security가 제공하는 Authentication 구현 class입니다. 프로젝트가 만든 class가 아닙니다.

로그인에서는 email과 password를 AuthenticationManager에 전달하는 인증 전 입력 객체로 사용합니다. 그러나 현재 Filter에서는 이미 JWT 서명과 User 조회가 끝났으므로 인증 결과를 담는 객체로 사용합니다.

- userDetails: principal, 즉 현재 인증 사용자
- null: JWT 경로에서는 password를 다시 검증하지 않으므로 credentials 없음
- getAuthorities(): 현재 사용자의 ROLE_USER 권한 목록

class 이름에 UsernamePassword가 있어도 이 Filter에서 password 로그인을 다시 한다는 뜻은 아닙니다.

### 이 파일의 코드 일부

~~~java
authentication.setDetails(
        new WebAuthenticationDetailsSource().buildDetails(request)
);

SecurityContextHolder.getContext().setAuthentication(authentication);
~~~

이 코드의 설명:

WebAuthenticationDetailsSource는 Spring Security가 제공하는 class입니다. buildDetails(request)는 현재 request의 remote address와 session 정보 같은 부가 details를 만들어 Authentication에 저장합니다.

details는 사용자의 신원인 principal이나 권한인 authorities와 다른 값입니다. 현재 프로젝트의 main code에서 getDetails를 직접 읽는 코드는 확인되지 않았지만, Spring Security 표준 Authentication 구조에 맞춰 request 부가 정보를 저장합니다.

SecurityContextHolder도 Spring Security가 제공하는 class입니다. getContext로 현재 요청의 SecurityContext를 가져오고 setAuthentication으로 방금 만든 인증 객체를 저장합니다.

이 저장이 있어야 뒤의 authenticated 규칙과 Controller가 현재 요청을 인증된 요청으로 판단할 수 있습니다.

### 이 파일의 코드 일부

~~~java
filterChain.doFilter(request, response);
~~~

이 코드의 설명:

인증 성공 후 요청을 다음 Filter로 넘깁니다.

~~~text
JwtAuthenticationFilter
→ 다음 Security Filter
→ DispatcherServlet
→ Controller
~~~

이 method를 호출하지 않으면 현재 Filter에서 요청이 멈춥니다. 이 Filter가 Controller를 직접 호출하는 것은 아니며, Filter Chain이 끝난 뒤 DispatcherServlet이 Controller를 찾습니다.

### 이 파일의 코드 일부

~~~java
} catch (AuthException | DataNullException e) {
    SecurityContextHolder.clearContext();
    errorResponseWriter.write(
            response,
            org.springframework.http.HttpStatus.UNAUTHORIZED,
            ApiErrorCode.INVALID_TOKEN
    );
}
~~~

이 코드의 설명:

catch (AuthException | DataNullException e)는 Java multi-catch 문법입니다. JwtProvider의 검증 실패, User 조회 실패, 비활성 User, authVersion 불일치에서 발생한 두 예외를 같은 실패 흐름으로 처리합니다.

clearContext는 실패한 Authentication이 현재 실행 흐름에 남지 않도록 SecurityContext를 비웁니다. User Entity나 DB를 삭제하는 것이 아니라 현재 요청의 인증 정보만 제거합니다.

ErrorResponseWriter는 Controller의 GlobalExceptionHandler가 아니라 Filter에서 직접 호출하는 Bean입니다. write는 다음 작업을 합니다.

~~~text
response.setStatus(401)
→ application/json content type 설정
→ ApiErrorCode.INVALID_TOKEN으로 ErrorResponseDto 생성
→ ObjectMapper가 JSON으로 변환
→ response.getWriter()에 body 기록
~~~

이 catch에서는 filterChain.doFilter를 호출하지 않습니다. 따라서 실패한 요청은 Controller에 도달하지 않고 Filter가 현재 HttpServletResponse에 401 JSON을 기록한 뒤 끝납니다.

---

---

## 앞선 로그인 문서와의 연결

`CustomUserDetails`와 `CustomUserDetailsService`의 전체 원문은 흐름 1 문서에 둔다. 이 흐름에서는 Filter가 `loadUserByUserId`로 활성 User를 다시 조회하고, `UsernamePasswordAuthenticationToken`을 만들어 `SecurityContextHolder`에 저장하는 부분만 확인한다.
