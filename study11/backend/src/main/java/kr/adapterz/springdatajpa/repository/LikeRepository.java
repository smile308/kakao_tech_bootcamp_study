package kr.adapterz.springdatajpa.repository;

import kr.adapterz.springdatajpa.entity.Like;
import kr.adapterz.springdatajpa.entity.Post;
import kr.adapterz.springdatajpa.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LikeRepository extends JpaRepository<Like, Long> {

    boolean existsByPostAndUser(Post post, User user);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            DELETE FROM Like postLike
            WHERE postLike.post.postId = :postId
              AND postLike.user.userId = :userId
            """)
    int deleteByPostIdAndUserId(
            @Param("postId") Long postId,
            @Param("userId") Long userId
    );
}
