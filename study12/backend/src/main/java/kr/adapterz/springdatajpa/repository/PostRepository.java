package kr.adapterz.springdatajpa.repository;

import kr.adapterz.springdatajpa.entity.Post;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface PostRepository extends JpaRepository<Post, Long> {

    @EntityGraph(attributePaths = {"user", "postImages", "postCounter"})
    @Query("""
            SELECT post
            FROM Post post
            WHERE post.deleted = false
              AND post.postCounter.reportCount < :reportCount
            ORDER BY post.postId DESC
            """)
    Page<Post> findByDeletedFalseAndReportCountLessThanOrderByPostIdDesc(
            @Param("reportCount") int reportCount,
            Pageable pageable
    );

    @EntityGraph(attributePaths = {"user", "postImages", "postCounter"})
    @Query("""
            SELECT post
            FROM Post post
            WHERE post.postId = :postId
              AND post.deleted = false
            """)
    Optional<Post> findByPostIdAndDeletedFalse(
            @Param("postId") Long postId
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @EntityGraph(attributePaths = "postCounter")
    @Query("""
            SELECT post
            FROM Post post
            WHERE post.postId = :postId
              AND post.deleted = false
            """)
    Optional<Post> findActivePostForUpdate(@Param("postId") Long postId);

    @Query("""
            SELECT post
            FROM Post post
            WHERE post.postId = :postId
              AND post.deleted = false
            """)
    Optional<Post> findActivePostForInteraction(
            @Param("postId") Long postId
    );

    @Lock(LockModeType.PESSIMISTIC_READ)
    @Query("""
            SELECT post
            FROM Post post
            WHERE post.postId = :postId
              AND post.deleted = false
            """)
    Optional<Post> findActivePostForInteractionCheck(
            @Param("postId") Long postId
    );

}
