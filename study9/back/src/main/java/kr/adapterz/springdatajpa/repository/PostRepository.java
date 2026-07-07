package kr.adapterz.springdatajpa.repository;

import kr.adapterz.springdatajpa.entity.Post;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PostRepository extends JpaRepository<Post, Long> {

    Page<Post> findByDeletedFalseOrderByPostIdDesc(Pageable pageable);

    @EntityGraph(attributePaths = {"user", "postImages"})
    Optional<Post> findByPostIdAndDeletedFalse(Long postId);

}