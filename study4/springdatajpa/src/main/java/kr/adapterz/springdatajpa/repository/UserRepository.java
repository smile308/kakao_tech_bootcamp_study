package kr.adapterz.springdatajpa.repository;

import kr.adapterz.springdatajpa.entity.User;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserRepository {
    User save(User user);
    Optional<User> findById(Long id);
    Optional<User> findByEmailAndPassword(String email, String password);
    void deleteById(Long id);
}