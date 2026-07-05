package kr.adapterz.springdatajpa.entity;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

class LikeTest {

    @Test
    @DisplayName("좋아요 생성 시 게시글과 유저가 정상적으로 설정된다")
    void createLike() {
        // given
        User user = new User("test@test.com", "Password1!", "tester");
        Post post = new Post(user, "title", "content");

        // when
        Like like = new Like(post, user);

        // then
        assertAll(
                () -> assertThat(like.getPost()).isEqualTo(post),
                () -> assertThat(like.getUser()).isEqualTo(user)
        );
    }
}