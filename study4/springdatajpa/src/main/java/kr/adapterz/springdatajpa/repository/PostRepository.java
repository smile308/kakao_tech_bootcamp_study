package kr.adapterz.springdatajpa.repository;

import kr.adapterz.springdatajpa.entity.Post;

import java.util.List;
import java.util.Optional;

public interface PostRepository {

    Post save(Post post);

    List<Post> findAll();

    void deleteById(Long id);
}