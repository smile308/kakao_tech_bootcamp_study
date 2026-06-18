package kr.adapterz.springdatajpa.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.AuditorAware;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

import java.util.Optional;
import java.util.UUID;

@Configuration
@EnableJpaAuditing
public class AuditingConfig {

    @Bean
    public AuditorAware<String> auditorProvider() {
        // 임의 사용자 ID 반환
        // 실제 애플리케이션에서는 SecurityContextHolder 등에서 현재 사용자 정보를 가져와야 함
        return () -> Optional.of("local-" + UUID.randomUUID().toString());
    }
}