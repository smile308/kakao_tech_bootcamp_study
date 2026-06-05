package kr.adapterz.springdatajpa.db;

import jakarta.annotation.PostConstruct;
import kr.adapterz.springdatajpa.entity.Comment;
import kr.adapterz.springdatajpa.repository.CommentRepository;
import org.springframework.stereotype.Repository;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@Repository
public class CommentDb implements CommentRepository {

    private final Map<Long, Comment> store = new HashMap<>();
    //comment_id에 사용될 변수
    private Long sequence = 1L;

    //더미 데이터
    @PostConstruct
    public void init() {
        save(new Comment(
                1L,
                1L,
                "첫 번째 게시글의 첫 번째 댓글입니다."
        ));

        save(new Comment(
                1L,
                2L,
                "첫 번째 게시글의 두 번째 댓글입니다."
        ));

        save(new Comment(
                2L,
                1L,
                "두 번째 게시글의 첫 번째 댓글입니다."
        ));
    }

    //데이터 저장
    @Override
    public Comment save(Comment comment) {
        Long id = sequence++;

        Comment savedComment = new Comment(
                id,
                comment.getPost_id(),
                comment.getUser_id(),
                comment.getComment_content()
        );

        store.put(id, savedComment);

        return savedComment;
    }



    //코멘트 삭제
    @Override
    public void deleteById(Long id) {
        store.remove(id);
    }
}