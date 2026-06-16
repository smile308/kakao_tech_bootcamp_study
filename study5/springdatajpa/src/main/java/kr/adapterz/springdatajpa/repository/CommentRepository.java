package kr.adapterz.springdatajpa.repository;

import kr.adapterz.springdatajpa.entity.Comment;

import java.util.List;
import java.util.Optional;

public interface CommentRepository {

    Comment save(Comment comment);

    List<Comment> findByPostId(Long postId);

    Optional<Comment> findId(Long commentId);

    void deleteById(Long id);
}