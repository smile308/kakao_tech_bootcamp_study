package kr.adapterz.springdatajpa.repository;

import kr.adapterz.springdatajpa.entity.Post;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface PostRepository extends JpaRepository<Post, Long> {

    Page<Post> findByDeletedFalseAndReportCountLessThanOrderByPostIdDesc(
            int reportCount,
            Pageable pageable
    );

    @EntityGraph(attributePaths = {"user", "postImages"})
    Optional<Post> findByPostIdAndDeletedFalse(Long postId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT post
            FROM Post post
            WHERE post.postId = :postId
              AND post.deleted = false
            """)
    Optional<Post> findActivePostForUpdate(@Param("postId") Long postId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE Post post
            SET post.viewCount = post.viewCount + 1
            WHERE post.postId = :postId
              AND post.deleted = false
              AND post.reportCount < :reportBlockThreshold
            """)
    int incrementViewCount(
            @Param("postId") Long postId,
            @Param("reportBlockThreshold") int reportBlockThreshold
    );

}
