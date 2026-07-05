package kr.adapterz.springdatajpa.entity;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class CommentTest {
    @Test
    @DisplayName("댓글 생성 시 기본값이 설정된다")
    void createComment() {
        User user = new User("test@test.com", "Password1!", "tester");
        Post post = new Post(user, "title", "content");

        Comment comment = new Comment(user, post, "댓글");

        assertThat(comment.getUser()).isEqualTo(user);
        assertThat(comment.getPost()).isEqualTo(post);
        assertThat(comment.getCommentContent()).isEqualTo("댓글");
        assertThat(comment.getOriginId()).isNull();
    }
}
