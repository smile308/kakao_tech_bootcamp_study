package kr.adapterz.springdatajpa.entity;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

class PostCounterTest {

    @Test
    void 게시글_카운터는_게시글과_함께_생성되고_모든_값이_0으로_초기화된다() {
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
                () -> assertThat(counter.getReplyCount()).isZero()
        );
    }
}
