package kr.adapterz.springdatajpa.repository;

import kr.adapterz.springdatajpa.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmailAndPasswordAndDeletedFalse(String email, String password);

    Optional<User> findByUserIdAndDeletedFalse(Long userId);

    boolean existsByEmailAndDeletedFalse(String email);

    boolean existsByNicknameAndDeletedFalse(String nickname);

    boolean existsByNicknameAndDeletedFalseAndUserIdNot(String nickname, Long userId);
}