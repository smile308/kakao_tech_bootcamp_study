package kr.adapterz.springdatajpa.service;

import kr.adapterz.springdatajpa.repository.AuthSessionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Component
@RequiredArgsConstructor
public class AuthSessionCleanupScheduler {

    private final AuthSessionRepository authSessionRepository;

    @Scheduled(
            initialDelayString = "${jwt.refresh-session-cleanup-interval-millis}",
            fixedDelayString = "${jwt.refresh-session-cleanup-interval-millis}"
    )
    @Transactional
    public void deleteExpiredSessions() {
        authSessionRepository.deleteAllExpiredAtOrBefore(LocalDateTime.now());
    }
}
