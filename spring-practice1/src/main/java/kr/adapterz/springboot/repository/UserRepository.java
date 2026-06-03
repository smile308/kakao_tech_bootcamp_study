package kr.adapterz.springboot.repository;

import kr.adapterz.springboot.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, Long> {
}