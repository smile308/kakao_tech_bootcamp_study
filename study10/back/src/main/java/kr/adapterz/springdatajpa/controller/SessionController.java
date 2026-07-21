package kr.adapterz.springdatajpa.controller;

import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import kr.adapterz.springdatajpa.auth.RefreshCookieProvider;
import kr.adapterz.springdatajpa.dto.user.SessionRefreshResponseDto;
import kr.adapterz.springdatajpa.dto.user.SessionRequestDto;
import kr.adapterz.springdatajpa.dto.user.SessionResponseDto;
import kr.adapterz.springdatajpa.service.SessionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/sessions")
@RequiredArgsConstructor
public class SessionController {
    private final SessionService sessionService;
    private final RefreshCookieProvider refreshCookieProvider;

    //로그인
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public SessionResponseDto createSession(
            @Valid @RequestBody SessionRequestDto request,
            HttpServletResponse response
    ){
        SessionResponseDto sessionResponse = sessionService.createSession(request);

        response.addHeader(
                HttpHeaders.SET_COOKIE,
                refreshCookieProvider
                        .createRefreshTokenCookie(sessionResponse.getRefreshToken())
                        .toString()
        );

        return sessionResponse;
    }

    //액세스 토큰 재발급
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

    //로그아웃
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
}
