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

@Service
@RequiredArgsConstructor
@Transactional
public class SessionService {

    private final AuthenticationManager authenticationManager;
    private final JwtProvider jwtProvider;
    private final RefreshTokenProvider refreshTokenProvider;
    private final AuthSessionRepository authSessionRepository;

    public SessionResponseDto createSession(SessionRequestDto request) {
        try {
            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(
                            request.getEmail(),
                            request.getPassword()
                    )
            );

            CustomUserDetails userDetails = (CustomUserDetails) authentication.getPrincipal();

            String accessToken = jwtProvider.createAccessToken(userDetails.getUserId());
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

    public void deleteSession(String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) {
            return;
        }

        String refreshTokenHash = refreshTokenProvider.hashRefreshToken(refreshToken);
        authSessionRepository
                .findByRefreshTokenHash(refreshTokenHash)
                .ifPresent(authSession -> authSession.revoke(LocalDateTime.now()));
    }

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
                authSession.getUser().getUserId()
        );

        return new SessionRefreshResponseDto(
                accessToken,
                newRefreshToken
        );
    }
}
