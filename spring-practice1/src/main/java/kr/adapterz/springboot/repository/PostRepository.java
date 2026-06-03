package kr.adapterz.springboot.repository;

import kr.adapterz.springboot.entity.Post;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PostRepository extends JpaRepository<Post, Long> {
}