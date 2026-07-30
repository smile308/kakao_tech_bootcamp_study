package kr.adapterz.springdatajpa.entity;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

class PostViewCountTest {

    @Test
    @DisplayName("조회수 카운터는 게시글과 함께 생성되고 0으로 초기화된다")
    void createPostViewCountDefaultValue() {
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
