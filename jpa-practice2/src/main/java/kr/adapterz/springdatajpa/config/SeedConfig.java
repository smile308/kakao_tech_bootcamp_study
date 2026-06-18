package kr.adapterz.springdatajpa.config;


import jakarta.transaction.Transactional;
import kr.adapterz.springdatajpa.entity.Post;
import kr.adapterz.springdatajpa.entity.User;
import kr.adapterz.springdatajpa.repository.PostRepository;
import kr.adapterz.springdatajpa.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import java.util.stream.IntStream;

import static org.hibernate.engine.internal.Versioning.seed;

@Configuration
@Profile("dev")
@RequiredArgsConstructor
public class SeedConfig {
    private final UserRepository userRepository;
    private final PostRepository postRepository;

    @Bean
    ApplicationRunner seedRunner() {
        return args ->seed();
    }
    @Transactional
    void seed() {
        if (userRepository.count() >= 10 && postRepository.count() >= 10) return;

        IntStream.rangeClosed(1, 10).forEach(i -> {
            User user = new User("tester"+i+"@adapterz.kr", "123aS!"+i, "tester"+i);
            userRepository.save(user);

            Post post = new Post("title"+i, "content"+i, user);
            postRepository.save(post);
        });
    }
}
