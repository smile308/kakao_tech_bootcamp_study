package kr.adapterz.springdatajpa.repository;

import jakarta.persistence.LockModeType;
import kr.adapterz.springdatajpa.entity.PostCounter;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface PostCounterRepository extends JpaRepository<PostCounter, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT counter
            FROM PostCounter counter
            WHERE counter.postId = :postId
            """)
    Optional<PostCounter> findByPostIdForUpdate(
            @Param("postId") Long postId
    );

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE PostCounter counter
            SET counter.viewCount = counter.viewCount + 1
            WHERE counter.postId = :postId
              AND counter.reportCount < :reportBlockThreshold
            """)
    int incrementViewCount(
            @Param("postId") Long postId,
            @Param("reportBlockThreshold") int reportBlockThreshold
    );

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE PostCounter counter
            SET counter.likeCount = counter.likeCount + 1
            WHERE counter.postId = :postId
            """)
    int incrementLikeCount(@Param("postId") Long postId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE PostCounter counter
            SET counter.likeCount = counter.likeCount - 1
            WHERE counter.postId = :postId
              AND counter.likeCount > 0
            """)
    int decrementLikeCount(@Param("postId") Long postId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE PostCounter counter
            SET counter.replyCount = counter.replyCount + 1
            WHERE counter.postId = :postId
            """)
    int incrementReplyCount(@Param("postId") Long postId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE PostCounter counter
            SET counter.replyCount = counter.replyCount - 1
            WHERE counter.postId = :postId
              AND counter.replyCount > 0
            """)
    int decrementReplyCount(@Param("postId") Long postId);
}
