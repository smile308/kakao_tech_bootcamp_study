package kr.adapterz.springdatajpa.repository;

import kr.adapterz.springdatajpa.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmailAndPassword(String email, String password);

    boolean existsByEmail(String email);

    boolean existsByNickname(String nickname);
}