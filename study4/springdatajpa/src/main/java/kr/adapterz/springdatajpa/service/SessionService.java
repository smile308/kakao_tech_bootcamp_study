package kr.adapterz.springdatajpa.service;

import kr.adapterz.springdatajpa.dto.user.SessionDeleteRequestDto;
import kr.adapterz.springdatajpa.dto.user.SessionDeleteResponseDto;
import kr.adapterz.springdatajpa.dto.user.SessionRequestDto;
import kr.adapterz.springdatajpa.dto.user.SessionResponseDto;
import kr.adapterz.springdatajpa.entity.Session;
import kr.adapterz.springdatajpa.entity.User;
import kr.adapterz.springdatajpa.exception.LoginFailedException;
import kr.adapterz.springdatajpa.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class SessionService {
    private final UserRepository userRepository;

    //로그인
    public SessionResponseDto createSession(SessionRequestDto request){
        User user = userRepository.findByEmailAndPassword(request.getEmail(), request.getPassword()).orElseThrow(() -> new LoginFailedException("Login_Failed"));
        Session session = new Session(user.getUser_id());
        return new SessionResponseDto(session);
    }

    //로그아웃 현재 세션을 000000으로 사용중이라 미구현
    public SessionDeleteResponseDto deleteSession(SessionDeleteRequestDto request){
        SessionDeleteResponseDto sessionDeleteResponseDto = new SessionDeleteResponseDto();

        return sessionDeleteResponseDto;
    }
}
