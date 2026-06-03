package kr.adapterz.springdatajpa.service;

import kr.adapterz.springdatajpa.dto.user.SessionRequestDto;
import kr.adapterz.springdatajpa.dto.user.SessionResponseDto;
import kr.adapterz.springdatajpa.entity.Session;
import kr.adapterz.springdatajpa.entity.User;
import kr.adapterz.springdatajpa.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class SessionService {
    private final UserRepository userRepository;

    public SessionResponseDto createSession(SessionRequestDto request){
        User user = userRepository.findByEmailAndPassword(request.getEmail(), request.getPassword()).orElseThrow(()->new RuntimeException("Login_Failed"));
        Session session = new Session(user.getUser_id());
        return new SessionResponseDto(session);
    }
}
