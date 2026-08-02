package kr.adapterz.springdatajpa.entity;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

class PostViewCountTest {

    @Test
    void 조회수_카운터는_게시글과_함께_생성되고_0으로_초기화된다() {
        User user = new User(
                "view-counter@test.com",
                "Password1!",
                "view-counter",
                "profile.png",
                0
        );

        Post post = new Post(user, "title", "content");
        PostViewCount viewCount = post.getPostViewCount();

        assertAll(
                () -> assertThat(viewCount.getPost()).isEqualTo(post),
                () -> assertThat(viewCount.getViewCount()).isZero()
        );
    }
}
