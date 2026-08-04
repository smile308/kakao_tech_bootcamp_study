# 4단계-5. SessionService 전체 흐름

## 파일 위치와 책임

파일: `/Users/miles/Documents/GitHub/KTB4_Miles_Week12_Back/src/main/java/kr/adapterz/springdatajpa/service/SessionService.java`

이 Service는 로그인 인증, Refresh Token 검증·교체, 로그아웃 처리를 하나의 transaction 흐름으로 관리한다.

## 전체 실행 흐름

```text
createSession
→ AuthenticationManager로 이메일·비밀번호 검증
→ Access Token 생성
→ Refresh Token 원문·hash 생성
→ AuthSession 저장
→ 응답 DTO 반환

refreshSession
→ Cookie 원문 hash
→ 비관적 lock 조회
→ 활성·사용자 상태 검사
→ Refresh Token rotate
→ 새 Access Token·Cookie 값 반환
```

## 전체 주석본

```java
package kr.adapterz.springdatajpa.service;

import kr.adapterz.springdatajpa.auth.CustomUserDetails;
import kr.adapterz.springdatajpa.auth.JwtProvider;
import kr.adapterz.springdatajpa.auth.RefreshTokenProvider;
import kr.adapterz.springdatajpa.dto.user.SessionRefreshResponseDto;
import kr.adapterz.springdatajpa.dto.user.SessionRequestDto;
import kr.adapterz.springdatajpa.dto.user.SessionResponseDto;
import kr.adapterz.springdatajpa.entity.AuthSession;
import kr.adapterz.springdatajpa.exception.AuthException;
import kr.adapterz.springdatajpa.exception.LoginFailedException;
import kr.adapterz.springdatajpa.repository.AuthSessionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service // Spring이 업무 Service Bean으로 등록한다.
@RequiredArgsConstructor // final field를 받는 생성자를 Lombok이 생성한다.
@Transactional // public method 실행을 기본적으로 transaction 안에서 처리한다.
public class SessionService {

    private final AuthenticationManager authenticationManager; // 로그인 자격 증명을 검증한다.
    private final JwtProvider jwtProvider; // Access Token을 생성한다.
    private final RefreshTokenProvider refreshTokenProvider; // Refresh Token 생성·hash·만료 계산을 담당한다.
    private final AuthSessionRepository authSessionRepository; // AuthSession을 DB에 저장·조회한다.

    public SessionResponseDto createSession(SessionRequestDto request) { // 로그인 요청을 처리한다.
        try { // Spring Security 인증 예외를 프로젝트 예외로 변환할 범위다.
            Authentication authentication = authenticationManager.authenticate( // 이메일·비밀번호를 검증한다.
                    new UsernamePasswordAuthenticationToken( // 인증에 사용할 자격 증명 객체를 만든다.
                            request.getEmail(), // 로그인 DTO에서 이메일을 가져온다.
                            request.getPassword() // 로그인 DTO에서 비밀번호를 가져온다.
                    )
            );

            CustomUserDetails userDetails = (CustomUserDetails) authentication.getPrincipal(); // 인증 성공 후 사용자 정보를 꺼낸다.

            String accessToken = jwtProvider.createAccessToken( // 사용자 ID·인증 버전으로 Access Token을 만든다.
                    userDetails.getUserId(),
                    userDetails.getAuthVersion()
            );
            String refreshToken = refreshTokenProvider.createRefreshToken(); // Cookie에 넣을 원문 Refresh Token을 만든다.
            String refreshTokenHash = refreshTokenProvider.hashRefreshToken(refreshToken); // DB 저장용 hash를 만든다.

            AuthSession authSession = new AuthSession( // 새 로그인 세션 Entity를 만든다.
                    userDetails.getUser(), // 세션과 연결할 User Entity다.
                    refreshTokenHash, // 원문이 아닌 hash를 저장한다.
                    refreshTokenProvider.createExpirationTime() // DB 세션 만료 시각을 계산한다.
            );
            authSessionRepository.save(authSession); // AuthSession INSERT를 transaction에 등록한다.

            return new SessionResponseDto( // Controller가 응답과 Cookie를 만들 수 있는 DTO를 반환한다.
                    accessToken,
                    refreshToken,
                    userDetails.getUserId()
            );

        } catch (DisabledException e) { // 계정이 비활성화된 경우다.
            throw new LoginFailedException("Suspended_Account"); // 프로젝트 로그인 실패 예외로 변환한다.
        } catch (AuthenticationException e) { // 그 외 인증 실패다.
            throw new LoginFailedException("Login_Failed"); // 외부 응답용 오류 code로 변환한다.
        }
    }

    public void deleteSession(String refreshToken) { // 로그아웃 요청을 처리한다.
        if (refreshToken == null || refreshToken.isBlank()) { // Cookie가 없거나 비어 있는지 확인한다.
            return; // 삭제할 세션이 없으면 정상 종료한다.
        }

        String refreshTokenHash = refreshTokenProvider.hashRefreshToken(refreshToken); // Cookie 원문을 DB 조회용 hash로 바꾼다.
        authSessionRepository
                .findByRefreshTokenHash(refreshTokenHash) // 비관적 lock으로 세션을 조회한다.
                .ifPresent(authSession -> authSession.revoke(LocalDateTime.now())); // 있으면 현재 시각으로 revoke한다.
    }

    public SessionRefreshResponseDto refreshSession(String refreshToken) { // Refresh Token으로 새 인증 정보를 발급한다.
        if (refreshToken == null || refreshToken.isBlank()) { // Cookie가 없거나 비어 있는지 확인한다.
            throw new AuthException("Invalid_Refresh_Token"); // 유효하지 않은 Refresh Token으로 처리한다.
        }

        String refreshTokenHash = refreshTokenProvider.hashRefreshToken(refreshToken); // 원문을 DB 조회용 hash로 변환한다.
        AuthSession authSession = authSessionRepository
                .findByRefreshTokenHash(refreshTokenHash) // hash로 row를 비관적 쓰기 잠금 조회한다.
                .orElseThrow(() -> new AuthException("Invalid_Refresh_Token")); // row가 없으면 인증 실패다.

        if (!authSession.isActive(LocalDateTime.now())) { // revoke·만료 여부를 확인한다.
            throw new AuthException("Invalid_Refresh_Token"); // 비활성 세션이면 재발급하지 않는다.
        }

        if (authSession.getUser().isDeleted() || authSession.getUser().isSuspended()) { // 연결된 User 상태를 확인한다.
            throw new AuthException("Invalid_Refresh_Token"); // 탈퇴·정지 사용자는 재발급할 수 없다.
        }

        String newRefreshToken = refreshTokenProvider.createRefreshToken(); // 새 원문 Refresh Token을 만든다.
        String newRefreshTokenHash = refreshTokenProvider.hashRefreshToken( // 새 원문의 hash를 만든다.
                newRefreshToken
        );
        authSession.rotate( // 같은 AuthSession row의 hash와 만료 시각을 새 값으로 교체한다.
                newRefreshTokenHash,
                refreshTokenProvider.createExpirationTime()
        );

        String accessToken = jwtProvider.createAccessToken( // User의 최신 ID·인증 버전으로 Access Token을 만든다.
                authSession.getUser().getUserId(),
                authSession.getUser().getAuthVersion()
        );

        return new SessionRefreshResponseDto( // 새 Access Token과 Cookie용 원문을 반환한다.
                accessToken,
                newRefreshToken
        );
    }
}
```

## 설명 주석본

```java
package kr.adapterz.springdatajpa.service;

import kr.adapterz.springdatajpa.auth.CustomUserDetails;
import kr.adapterz.springdatajpa.auth.JwtProvider;
import kr.adapterz.springdatajpa.auth.RefreshTokenProvider;
import kr.adapterz.springdatajpa.dto.user.SessionRefreshResponseDto;
import kr.adapterz.springdatajpa.dto.user.SessionRequestDto;
import kr.adapterz.springdatajpa.dto.user.SessionResponseDto;
import kr.adapterz.springdatajpa.entity.AuthSession;
import kr.adapterz.springdatajpa.exception.AuthException;
import kr.adapterz.springdatajpa.exception.LoginFailedException;
import kr.adapterz.springdatajpa.repository.AuthSessionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/*
 * 1. Service·의존성·transaction
 * 로그인, 로그아웃, Refresh를 업무 흐름으로 묶는다.
 * final field들은 Lombok 생성자를 통해 Spring Bean으로 주입된다.
 * @Transactional은 Entity 조회·변경·저장을 하나의 DB 작업 범위로 묶는다.
 */
/* @Service: Spring Service Bean으로 등록한다. */
@Service
/* @RequiredArgsConstructor: final field를 받는 생성자를 Lombok이 생성한다. */
@RequiredArgsConstructor
/* @Transactional: 이 class의 public method을 DB transaction 안에서 실행한다. */
@Transactional
public class SessionService {
    private final AuthenticationManager authenticationManager;
    private final JwtProvider jwtProvider;
    private final RefreshTokenProvider refreshTokenProvider;
    private final AuthSessionRepository authSessionRepository;

    /*
     * 2. 로그인
     * DTO의 email/password를 AuthenticationManager에 전달한다.
     * 성공하면 CustomUserDetails를 얻고 Access Token과 Refresh Token을 만든다.
     * DB에는 Refresh Token hash를 AuthSession으로 저장하고,
     * 응답에는 Access Token과 Cookie용 원문을 반환한다.
     */
    /* try-catch: Security 예외를 프로젝트 예외로 변환한다. */
    public SessionResponseDto createSession(SessionRequestDto request) {
        try {
            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(
                            request.getEmail(),
                            request.getPassword()
                    )
            );

            /* 형변환: Authentication이 반환한 principal을 프로젝트 사용자 타입으로 바꾼다. */
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

    /*
     * 3. 로그아웃
     * Cookie가 없으면 아무 DB 작업 없이 종료한다.
     * Cookie 원문을 hash로 바꿔 AuthSession을 찾고,
     * 찾은 Entity의 revoke 메서드가 transaction flush 때 revokedAt을 변경한다.
     */
    public void deleteSession(String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) {
            return;
        }

        String refreshTokenHash = refreshTokenProvider.hashRefreshToken(refreshToken);
        authSessionRepository
                .findByRefreshTokenHash(refreshTokenHash)
                /* Optional.ifPresent: 조회 결과가 있을 때만 lambda를 실행한다. */
                .ifPresent(authSession -> authSession.revoke(LocalDateTime.now()));
    }

    /*
     * 4. Refresh Token 재발급
     * 기존 원문을 hash하여 lock 조회하고, 활성·사용자 상태를 확인한다.
     * 검증을 통과하면 새 원문·hash를 만든 뒤 같은 AuthSession을 rotate한다.
     * 마지막으로 새 Access Token과 Cookie용 원문을 DTO로 반환한다.
     */
    public SessionRefreshResponseDto refreshSession(String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) {
            throw new AuthException("Invalid_Refresh_Token");
        }

        String refreshTokenHash = refreshTokenProvider.hashRefreshToken(refreshToken);
        AuthSession authSession = authSessionRepository
                .findByRefreshTokenHash(refreshTokenHash)
                /* Optional.orElseThrow: 결과가 없으면 지정한 예외를 던진다. */
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
```

## 코드 바로 아래 상세 설명

### 1. 클래스 annotation과 의존성

- `@Service`: Spring이 Service Bean으로 등록한다.
- `@RequiredArgsConstructor`: 네 개의 `final` field를 받는 생성자를 생성한다.
- `@Transactional`: 메서드에서 조회한 `AuthSession`을 `rotate`·`revoke`로 변경하면 transaction 종료 시 JPA가 DB에 반영한다.
- `AuthenticationManager`: 이메일·비밀번호 인증을 다른 Spring Security 구성 요소에 위임한다.
- `JwtProvider`: Access Token만 담당한다.
- `RefreshTokenProvider`: 원문·hash·만료 시각을 담당한다.
- `AuthSessionRepository`: Refresh Session row를 조회·저장한다.

### 2. `createSession` 로그인 흐름

호출 파일: `/Users/miles/Documents/GitHub/KTB4_Miles_Week12_Back/src/main/java/kr/adapterz/springdatajpa/controller/SessionController.java`

호출 메서드: `createSession`

매개변수: `@RequestBody SessionRequestDto request`

실행 순서:

1. `request.getEmail()`과 `request.getPassword()`를 `UsernamePasswordAuthenticationToken`에 넣는다.
2. `authenticationManager.authenticate(...)`가 실제 사용자 조회와 비밀번호 검증을 수행한다.
3. 성공하면 `authentication.getPrincipal()`을 `CustomUserDetails`로 형변환한다.
4. 사용자 ID와 authVersion으로 Access Token을 만든다.
5. 원문 Refresh Token과 hash를 각각 만든다.
6. hash·사용자·만료 시각으로 AuthSession을 생성한다.
7. `save`가 AuthSession 저장을 transaction에 등록한다.
8. Access Token·원문 Refresh Token·userId를 SessionResponseDto에 담아 Controller로 반환한다.

예외 흐름:

- `DisabledException` → `LoginFailedException("Suspended_Account")`
- 그 외 `AuthenticationException` → `LoginFailedException("Login_Failed")`
- 이후 `GlobalExceptionHandler`가 HTTP 401 응답으로 변환한다.

### 3. `deleteSession` 로그아웃 흐름

호출 파일: `/Users/miles/Documents/GitHub/KTB4_Miles_Week12_Back/src/main/java/kr/adapterz/springdatajpa/controller/SessionController.java`

호출 메서드: `deleteSession`

1. Cookie가 null 또는 blank면 정상 종료한다.
2. 원문 Refresh Token을 hash로 바꾼다.
3. Repository가 hash로 AuthSession을 찾는다.
4. 찾은 Entity에 `revoke(LocalDateTime.now())`를 호출한다.
5. Entity field 변경은 transaction flush 시 DB의 `revoked_at`에 반영된다.

조회 결과가 없어도 `Optional.ifPresent` 때문에 예외 없이 종료한다. Cookie 만료 응답은 Service가 아니라 Controller가 `RefreshCookieProvider`로 만든다.

### 4. `refreshSession` 재발급 흐름

호출 파일: `/Users/miles/Documents/GitHub/KTB4_Miles_Week12_Back/src/main/java/kr/adapterz/springdatajpa/controller/SessionController.java`

호출 메서드: `refreshSession`

매개변수: `@CookieValue`로 전달된 원문 Refresh Token

1. Cookie가 없으면 즉시 `Invalid_Refresh_Token` 예외를 던진다.
2. 원문을 hash로 바꾼다.
3. `findByRefreshTokenHash`가 비관적 쓰기 잠금으로 AuthSession을 조회한다.
4. row가 없으면 예외를 던진다.
5. `isActive`로 revoke 여부와 만료 여부를 확인한다.
6. 연결된 User가 삭제·정지 상태인지 확인한다.
7. 새 원문과 새 hash를 만든다.
8. `rotate`로 같은 AuthSession의 hash와 만료 시각을 변경한다.
9. 사용자 ID·authVersion으로 새 Access Token을 만든다.
10. 새 Access Token과 새 원문 Refresh Token을 DTO로 반환한다.

새 원문은 Controller가 Cookie에 넣고, DTO의 `@JsonIgnore` 때문에 JSON body에는 포함되지 않는다.

### 5. 같은 transaction이 필요한 이유

Refresh 흐름에서는 다음 작업이 하나의 원자적 범위에 있어야 한다.

```text
기존 세션 lock 조회
→ 활성 검사
→ rotate
→ 새 token 응답
→ transaction commit
```

`@Transactional`이 없으면 조회와 rotate가 서로 다른 DB 작업이 되어 동시 요청에서 같은 Refresh Token이 중복 사용될 가능성을 제어하기 어려워진다. Repository의 `PESSIMISTIC_WRITE`와 Service의 transaction이 함께 동시성 흐름을 구성한다.

## 새 문법·개념

- `AuthenticationManager.authenticate`: 인증 책임을 Spring Security에 위임하는 메서드다.
- `Authentication.getPrincipal`: 인증 성공 후 사용자 상세 객체를 꺼낸다.
- 형변환 `(CustomUserDetails)`: 반환 타입을 실제 프로젝트 사용자 타입으로 바꾼다.
- `Optional.ifPresent`: 값이 있을 때만 lambda를 실행한다.
- `orElseThrow`: Optional이 비어 있으면 지정한 예외를 던진다.
- `@Transactional`: DB 조회·변경·commit 범위를 선언한다.

## 다음 파일

`SessionController.java`에서 DTO와 Cookie가 HTTP 요청·응답으로 어떻게 연결되는지 읽는다.

현재 파일 진행률: **30개 확인 완료 / 최소 학습 대상 213개 = 약 14.1%**
