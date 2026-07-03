package kr.adapterz.springdatajpa.service;

import kr.adapterz.springdatajpa.auth.JwtProvider;
import kr.adapterz.springdatajpa.dto.user.SessionDeleteResponseDto;
import kr.adapterz.springdatajpa.dto.user.SessionRequestDto;
import kr.adapterz.springdatajpa.dto.user.SessionResponseDto;
import kr.adapterz.springdatajpa.entity.User;
import kr.adapterz.springdatajpa.exception.LoginFailedException;
import kr.adapterz.springdatajpa.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class SessionService {
    private final UserRepository userRepository;
    private final JwtProvider jwtProvider;

    //로그인
    public SessionResponseDto createSession(SessionRequestDto request){
        User user = userRepository.findByEmailAndPasswordAndDeletedFalse(request.getEmail(), request.getPassword())
                .orElseThrow(() -> new LoginFailedException("Login_Failed"));

        String accessToken = jwtProvider.createAccessToken(user.getUserId());

        return new SessionResponseDto(accessToken, user.getUserId());
    }

    //JWT 방식에서는 서버 세션을 삭제하지 않고, 클라이언트 저장 토큰을 제거한다.
    public SessionDeleteResponseDto deleteSession(String authorizationHeader){
        jwtProvider.getUserIdFromAuthorizationHeader(authorizationHeader);
        return new SessionDeleteResponseDto();
    }
}
