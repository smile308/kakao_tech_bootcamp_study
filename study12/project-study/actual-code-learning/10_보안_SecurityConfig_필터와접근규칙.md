# 4단계-7. SecurityConfig

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

현재 파일 진행률: **32개 확인 완료 / 최소 학습 대상 213개 = 약 15.0%**

다음 파일: `auth/JwtAuthenticationFilter.java`
