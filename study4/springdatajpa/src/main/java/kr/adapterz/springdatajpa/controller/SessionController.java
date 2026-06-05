package kr.adapterz.springdatajpa.controller;

import jakarta.validation.Valid;
import kr.adapterz.springdatajpa.dto.user.SessionDeleteRequestDto;
import kr.adapterz.springdatajpa.dto.user.SessionDeleteResponseDto;
import kr.adapterz.springdatajpa.dto.user.SessionRequestDto;
import kr.adapterz.springdatajpa.dto.user.SessionResponseDto;
import kr.adapterz.springdatajpa.service.SessionService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/sessions")
@RequiredArgsConstructor
public class SessionController {
    private final SessionService sessionService;

    //로그인
    @PostMapping
    public SessionResponseDto createSession(@Valid @RequestBody SessionRequestDto request){
        return sessionService.createSession(request);
    }

    //로그아웃
    @DeleteMapping
    public SessionDeleteResponseDto deleteSession(@RequestBody SessionDeleteRequestDto request){
        return sessionService.deleteSession(request);
    }
}
