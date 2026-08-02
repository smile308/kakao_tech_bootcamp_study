package kr.adapterz.springdatajpa.entity;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CommentTest {

    @Test
    void 댓글_내용을_수정하면_새로운_내용으로_변경된다() {
        User user = new User("test@test.com", "Password1!", "tester",0);
        Post post = new Post(user, "title", "content");
        Comment comment = new Comment(user, post, "old");

        comment.changeComment("new");

        assertThat(comment.getCommentContent()).isEqualTo("new");
    }
}