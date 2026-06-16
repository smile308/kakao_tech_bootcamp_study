package kr.adapterz.springdatajpa.repository;

import kr.adapterz.springdatajpa.entity.Comment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface CommentRepository extends JpaRepository<Comment, Long> {

    @Query("select c from Comment c where c.post_id = :postId")
    List<Comment> findByPostId(@Param("postId") Long postId);

    default Optional<Comment> findId(Long commentId) {
        return findById(commentId);
    }
}