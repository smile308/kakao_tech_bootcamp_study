package kr.adapterz.springdatajpa.db;


import jakarta.annotation.PostConstruct;
import kr.adapterz.springdatajpa.entity.Post;
import kr.adapterz.springdatajpa.repository.PostRepository;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Repository
public class PostDb implements PostRepository {

    private final Map<Long, Post> store = new HashMap<>();
    //post_id에 활용될 변수
    private Long sequence = 1L;

    //더미 데이터
    @PostConstruct
    public void init() {
        save(new Post(
                1L,
                "첫 번째 게시글",
                "첫 번째 게시글 내용입니다.",
                "image.png1"
        ));

        save(new Post(
                2L,
                "두 번째 게시글",
                "두 번째 게시글 내용입니다.",
                "image.png2"
        ));
    }

    //새로운 데이트 추가
    @Override
    public Post save(Post post) {
        Long id = sequence++;

        Post savedPost = new Post(
                id,
                post.getUser_id(),
                post.getPost_title(),
                post.getPost_content(),
                post.getImage_file()
        );

        store.put(id, savedPost);

        return savedPost;
    }

    //리스트 전체 반환
    @Override
    public List<Post> findAll() {
        return new ArrayList<>(store.values());
    }

    //postid로 포스트 찾기
    @Override
    public Optional<Post> findId(Long postId) {
        return Optional.ofNullable(store.get(postId));
    }

    //포스트 삭제
    @Override
    public void deleteById(Long id) {
        store.remove(id);
    }
}