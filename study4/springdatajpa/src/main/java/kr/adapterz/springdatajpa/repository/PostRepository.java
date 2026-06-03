package kr.adapterz.springdatajpa.repository;

import jakarta.annotation.PostConstruct;
import kr.adapterz.springdatajpa.entity.Post;
import org.springframework.stereotype.Repository;

import java.util.*;

@Repository
public class PostRepository {
    private final Map<Long, Post> store = new HashMap<>();
    private Long sequence = 1L;

    @PostConstruct
    public void initDummyData() {
        save(new Post(
                1L,
                "첫 번째 게시글",
                "첫 번째 게시글 내용입니다."
        ));

        save(new Post(
                2L,
                "두 번째 게시글",
                "두 번째 게시글 내용입니다."
        ));
    }

    public Post save(Post post) {
        Long id = sequence++;

        Post savedPost = new Post(
                id,
                post.getUser_id(),
                post.getPost_title(),
                post.getPost_content()
        );

        store.put(id, savedPost);

        return savedPost;
    }

    public List<Post> findAll() {
        return new ArrayList<>(store.values());
    }

    public Optional<Post> findById(Long id) {
        return Optional.ofNullable(store.get(id));
    }

    public void deleteById(Long id) {
        store.remove(id);
    }
}
