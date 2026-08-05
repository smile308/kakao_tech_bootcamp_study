# 4단계-8. Access Token 인증 Filter와 사용자 정보

이 문서는 세 파일을 한 Markdown 안에서 파일별로 분리합니다. 각 파일은 전체 코드를 먼저 보여주고, 그 아래에서 설명할 코드 일부를 다시 보여준 뒤 바로 설명합니다.

---

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

## CustomUserDetailsService.java

파일 경로:

/Users/miles/Documents/GitHub/KTB4_Miles_Week12_Back/src/main/java/kr/adapterz/springdatajpa/auth/CustomUserDetailsService.java

파일의 책임:

- 로그인 시 email로 User를 조회합니다.
- JWT Filter가 userId로 User를 조회하도록 합니다.
- User Entity를 CustomUserDetails로 변환합니다.

### CustomUserDetailsService.java 전체 코드

~~~java
package kr.adapterz.springdatajpa.auth;

import kr.adapterz.springdatajpa.entity.User;
import kr.adapterz.springdatajpa.exception.DataNullException;
import kr.adapterz.springdatajpa.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service // Spring이 이 class를 Service Bean으로 등록한다.
@RequiredArgsConstructor // final userRepository를 받는 생성자를 Lombok이 만든다.
public class CustomUserDetailsService implements UserDetailsService { // Spring Security 표준 interface를 구현한다.

    private final UserRepository userRepository; // DB User 조회를 Repository에 위임한다.

    @Override // UserDetailsService interface method를 구현한다.
    public CustomUserDetails loadUserByUsername(String email) throws UsernameNotFoundException { // email로 로그인 사용자를 조회한다.
        User user = userRepository.findByEmailAndDeletedFalse(email) // email이 같고 deleted=false인 User를 찾는다.
                .orElseThrow(() -> new UsernameNotFoundException("No_User")); // 없으면 표준 로그인 실패 예외를 만든다.

        return new CustomUserDetails(user); // User Entity를 Security wrapper로 감싼다.
    }

    public CustomUserDetails loadUserByUserId(Long userId) { // JWT claim의 userId로 사용자를 조회한다.
        User user = userRepository.findByUserIdAndDeletedFalse(userId) // userId가 같고 deleted=false인 User를 찾는다.
                .orElseThrow(() -> new DataNullException("No_User")); // 없으면 Filter catch로 갈 예외를 만든다.

        return new CustomUserDetails(user); // 조회한 User Entity를 wrapper로 감싼다.
    }
}
~~~

### 이 파일의 코드 일부

~~~java
@Service
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;
~~~

이 코드의 설명:

Service는 Spring이 관리하는 Service Bean이라는 뜻입니다. RequiredArgsConstructor는 UserRepository를 받는 생성자를 만들고 Spring이 Repository를 주입하게 합니다.

UserDetailsService는 Spring Security가 로그인할 때 사용자 정보를 조회하기 위해 제공하는 interface입니다. 이 class는 그 interface를 구현합니다.

userRepository는 이 Service가 직접 만든 객체가 아니라 Spring Data JPA Repository Bean으로 주입받은 객체입니다.

### 이 파일의 코드 일부

~~~java
@Override
public CustomUserDetails loadUserByUsername(String email)
        throws UsernameNotFoundException {
    User user = userRepository.findByEmailAndDeletedFalse(email)
            .orElseThrow(() -> new UsernameNotFoundException("No_User"));

    return new CustomUserDetails(user);
}
~~~

이 코드의 설명:

이 method는 Controller가 직접 호출하지 않습니다.

~~~text
SessionService.createSession
→ AuthenticationManager.authenticate
→ Spring Security AuthenticationProvider
→ loadUserByUsername(email)
→ UserRepository 조회
~~~

email은 SessionRequestDto에서 시작해 UsernamePasswordAuthenticationToken을 거쳐 AuthenticationProvider가 이 method에 전달합니다.

findByEmailAndDeletedFalse는 email이 같고 deleted가 false인 User를 Optional로 반환합니다. User가 없으면 orElseThrow가 Lambda를 실행해 UsernameNotFoundException을 생성합니다.

User가 있으면 CustomUserDetails로 감싸 반환합니다. 이 반환값은 로그인 성공 결과 Authentication의 principal이 됩니다.

여기서 orElseThrow와 throws는 이름은 비슷하지만 역할이 다릅니다.

- orElseThrow(...): 실행 중 Optional이 비어 있을 때 실제로 UsernameNotFoundException 객체를 만들어 던지는 코드입니다. User가 있으면 예외를 만들지 않고 User를 반환합니다.
- throws UsernameNotFoundException: 이 메서드가 UsernameNotFoundException을 호출자에게 전파할 수 있다는 사실을 메서드 선언에 적는 문법입니다. 이 선언 자체가 예외를 발생시키지는 않습니다.

UsernameNotFoundException은 Spring Security의 unchecked exception이므로 Java 컴파일러가 throws 선언을 강제하지 않습니다. 따라서 throws를 생략해도 컴파일할 수 있습니다. 현재 코드가 throws를 적은 이유는 UserDetailsService interface가 같은 예외 가능성을 선언하고 있고, 이 구현 메서드도 사용자를 찾지 못하면 해당 예외를 전파한다는 계약을 코드에 명시하기 위해서입니다.

실행 흐름은 다음과 같습니다.

~~~text
User가 존재함
→ Optional에 값 있음
→ orElseThrow가 예외를 만들지 않음
→ User 반환

User가 없음
→ Optional.empty
→ orElseThrow가 UsernameNotFoundException을 생성·throw
→ AuthenticationProvider와 AuthenticationManager를 거쳐
→ SessionService의 AuthenticationException 처리로 이동
~~~

즉 throws는 예외 가능성을 선언하고, orElseThrow는 조건이 맞지 않을 때 실제로 예외를 발생시킵니다.

### 이 파일의 코드 일부

~~~java
public CustomUserDetails loadUserByUserId(Long userId) {
    User user = userRepository.findByUserIdAndDeletedFalse(userId)
            .orElseThrow(() -> new DataNullException("No_User"));

    return new CustomUserDetails(user);
}
~~~

이 코드의 설명:

이 method는 UserDetailsService interface에서 자동으로 제공되는 method가 아닙니다. JwtAuthenticationFilter가 token의 userId로 User를 조회하기 위해 이 class에 직접 추가한 method입니다.

userId는 request body가 아니라 JwtProvider가 검증한 AccessTokenClaims.userId()에서 나옵니다.

User를 찾지 못하면 DataNullException이 발생하고 JwtAuthenticationFilter의 catch로 이동합니다. Filter는 이를 INVALID_TOKEN 401 응답으로 바꿉니다.

User가 있으면 new CustomUserDetails(user)가 실행되어 Entity가 Spring Security wrapper로 변환됩니다.

---

## CustomUserDetails.java

파일 경로:

/Users/miles/Documents/GitHub/KTB4_Miles_Week12_Back/src/main/java/kr/adapterz/springdatajpa/auth/CustomUserDetails.java

파일의 책임:

- User Entity를 Spring Security UserDetails 형태로 감쌉니다.
- userId, nickname, authVersion을 프로젝트 코드에 제공합니다.
- password, email, 권한, 활성 상태를 Spring Security에 제공합니다.

### CustomUserDetails.java 전체 코드

~~~java
package kr.adapterz.springdatajpa.auth;

import kr.adapterz.springdatajpa.entity.User;
import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;

@Getter // private user field의 getter를 Lombok이 만든다.
public class CustomUserDetails implements UserDetails { // User Entity를 Spring Security 형태로 감싼다.

    private final User user; // 실제 User Entity를 보관한다.

    public CustomUserDetails(User user) { // User Entity를 받아 wrapper를 만든다.
        this.user = user; // 생성자 매개변수를 field에 저장한다.
    }

    public Long getUserId() { // SessionService와 Filter가 사용자 ID를 읽을 때 사용한다.
        return user.getUserId(); // 내부 User Entity의 ID를 반환한다.
    }

    public String getNickname() { // 인증 사용자의 nickname이 필요한 코드가 사용한다.
        return user.getNickname(); // 내부 User Entity의 nickname을 반환한다.
    }

    public long getAuthVersion() { // JWT 발급과 token version 비교에 사용한다.
        return user.getAuthVersion(); // 현재 DB User의 authVersion을 반환한다.
    }

    @Override // UserDetails interface의 권한 method를 구현한다.
    public Collection<? extends GrantedAuthority> getAuthorities() { // 현재 권한 목록을 반환한다.
        return List.of(new SimpleGrantedAuthority("ROLE_USER")); // 모든 인증 사용자를 ROLE_USER로 표현한다.
    }

    @Override // UserDetails interface의 password method를 구현한다.
    public String getPassword() { // password 비교에 사용할 값을 반환한다.
        return user.getPassword(); // DB에 저장된 password hash를 반환한다.
    }

    @Override // UserDetails interface의 username method를 구현한다.
    public String getUsername() { // Spring Security 식별자 문자열을 반환한다.
        return user.getEmail(); // 이 프로젝트에서는 email을 username으로 사용한다.
    }

    @Override // UserDetails interface의 enabled method를 구현한다.
    public boolean isEnabled() { // User가 인증 가능한 상태인지 반환한다.
        return !user.isDeleted() && !user.isSuspended(); // 삭제되지 않았고 정지되지 않은 경우만 true다.
    }

}
~~~

### 이 파일의 코드 일부

~~~java
@Getter
public class CustomUserDetails implements UserDetails {

    private final User user;

    public CustomUserDetails(User user) {
        this.user = user;
    }
~~~

이 코드의 설명:

UserDetails는 Spring Security가 사용자 이름, password, 권한, 활성 상태를 읽기 위해 제공하는 interface입니다.

User Entity를 그대로 Security에 넘기지 않고 wrapper로 감싸는 이유는 DB Entity의 field와 Spring Security가 요구하는 method를 연결하기 위해서입니다.

생성자 매개변수 user는 CustomUserDetailsService가 Repository에서 조회한 User입니다. this.user는 현재 wrapper의 field이고 오른쪽 user는 생성자 매개변수입니다.

final이므로 생성 후 다른 User Entity로 교체하지 않습니다.

### 이 파일의 코드 일부

~~~java
public Long getUserId() {
    return user.getUserId();
}

public String getNickname() {
    return user.getNickname();
}

public long getAuthVersion() {
    return user.getAuthVersion();
}
~~~

이 코드의 설명:

이 method들은 UserDetails interface가 요구하는 method가 아니라 프로젝트가 직접 추가한 accessor입니다.

getUserId의 반환값은 SessionService가 JwtProvider.createAccessToken에 전달합니다. 이후 JWT claim이 되고, 보호 요청에서 Filter가 UserRepository 조회에 사용합니다.

getNickname은 인증 사용자의 nickname이 필요한 업무 코드가 사용합니다.

getAuthVersion은 token 발급 시 claim으로 저장되고, JwtAuthenticationFilter가 현재 DB version과 비교합니다.

### 이 파일의 코드 일부

~~~java
@Override
public Collection<? extends GrantedAuthority> getAuthorities() {
    return List.of(new SimpleGrantedAuthority("ROLE_USER"));
}
~~~

이 코드의 설명:

UserDetails interface가 권한 Collection을 요구하므로 이 method를 구현합니다.

SimpleGrantedAuthority는 Spring Security가 제공하는 권한 객체입니다. 문자열 ROLE_USER를 권한 객체로 감쌉니다.

List.of는 권한 하나를 담은 List를 반환합니다. 이 반환값은 JwtAuthenticationFilter가 UsernamePasswordAuthenticationToken을 만들 때 세 번째 인자로 전달합니다.

권한 목록이 있다고 endpoint가 자동으로 공개되는 것은 아닙니다. 실제 endpoint 접근 규칙은 SecurityConfig가 결정합니다.

### 이 파일의 코드 일부

~~~java
@Override
public String getPassword() {
    return user.getPassword();
}

@Override
public String getUsername() {
    return user.getEmail();
}
~~~

이 코드의 설명:

getPassword는 원문 password가 아니라 DB에 저장된 password hash를 반환합니다. Spring Security가 로그인 입력값과 이 hash를 PasswordEncoder 방식으로 비교합니다.

getUsername이라는 method 이름이지만 이 프로젝트에서는 email을 식별자로 사용합니다. 로그인 email이 Authentication을 거쳐 loadUserByUsername으로 전달되고, UserDetails의 username 값도 email입니다.

### 이 파일의 코드 일부

~~~java
@Override
public boolean isEnabled() {
    return !user.isDeleted() && !user.isSuspended();
}
~~~

이 코드의 설명:

isEnabled는 User Entity의 deleted와 suspended 상태를 Spring Security가 사용하는 boolean으로 변환합니다.

deleted가 false이고 suspended가 false일 때만 true입니다. 둘 중 하나라도 true이면 false가 되어 인증할 수 없습니다.

JwtAuthenticationFilter는 보호 요청마다 이 method를 호출해 현재 User 상태를 확인합니다.

---

## 세 파일의 연결 흐름

~~~text
로그인 POST /sessions
→ SessionService.createSession
→ AuthenticationManager.authenticate
→ CustomUserDetailsService.loadUserByUsername(email)
→ UserRepository
→ CustomUserDetails
→ password 검증
→ Access Token 발급

로그인 이후 보호 요청
→ Authorization: Bearer token
→ JwtAuthenticationFilter
→ JwtProvider.getAccessTokenClaims
→ CustomUserDetailsService.loadUserByUserId(userId)
→ UserRepository
→ CustomUserDetails
→ Authentication 생성
→ SecurityContextHolder 저장
→ Controller
~~~

## 파일 진행률

이번 작업은 세 파일을 새로 확인한 것이 아니라 문서 구조를 변경한 작업입니다.

~~~text
확인 완료: 35개
전체 기준: 최소 213개
진행률: 약 16.4%
~~~
