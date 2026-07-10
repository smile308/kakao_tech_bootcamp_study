package kr.adapterz.springdatajpa.entity;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CommentTest {

    @Test
    @DisplayName("댓글 내용을 수정하면 새로운 내용으로 변경된다")
    void changeComment() {
        // given
        User user = new User("test@test.com", "Password1!", "tester",0);
        Post post = new Post(user, "title", "content");
        Comment comment = new Comment(user, post, "old");

        // when
        comment.changeComment("new");

        // then
        assertThat(comment.getCommentContent()).isEqualTo("new");
    }
}