package kr.adapterz.springdatajpa.repository;

import kr.adapterz.springdatajpa.entity.Comment;

import java.util.List;
import java.util.Optional;

public interface CommentRepository {

    Comment save(Comment comment);


    Optional<Comment> findById(Long id);

    void deleteById(Long id);
}