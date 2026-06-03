package kr.adapterz.springdatajpa.repository;

import jakarta.annotation.PostConstruct;
import kr.adapterz.springdatajpa.entity.Comment;
import org.springframework.stereotype.Repository;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@Repository
public class CommentRepository {
    private final Map<Long, Comment> store = new HashMap<>();
    private Long sequence = 1L;

    @PostConstruct
    public void initDummyData() {
        save(new Comment(
                2L,
                1L,
                "첫 번째 게시글에 달린 댓글입니다."
        ));

        save(new Comment(
                1L,
                1L,
                "첫 번째 게시글에 달린 두 번째 댓글입니다."
        ));

        save(new Comment(
                1L,
                2L,
                "두 번째 게시글에 달린 댓글입니다."
        ));
    }

    public Comment save(Comment comment) {
        Long id = sequence++;

        Comment savedComment = new Comment(
                id,
                comment.getUser_id(),
                comment.getPost_id(),
                comment.getComment_content()
        );

        store.put(id, savedComment);

        return savedComment;
    }

    public Optional<Comment> findById(Long id) {
        return Optional.ofNullable(store.get(id));
    }

    public void deleteById(Long id) {
        store.remove(id);
    }
}
