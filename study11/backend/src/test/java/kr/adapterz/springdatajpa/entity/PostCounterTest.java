package kr.adapterz.springdatajpa.entity;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

class PostCounterTest {

    @Test
    @DisplayName("게시글 카운터는 게시글과 함께 생성되고 모든 값이 0으로 초기화된다")
    void createPostCounterDefaultValues() {
        User user = new User(
                "counter@test.com",
                "Password1!",
                "counter",
                "profile.png",
                0
        );

        Post post = new Post(user, "title", "content");
        PostCounter counter = post.getPostCounter();

        assertAll(
                () -> assertThat(counter.getPost()).isEqualTo(post),
                () -> assertThat(counter.getLikeCount()).isZero(),
                () -> assertThat(counter.getReportCount()).isZero(),
                () -> assertThat(counter.getReplyCount()).isZero(),
                () -> assertThat(counter.getViewCount()).isZero()
        );
    }
}
