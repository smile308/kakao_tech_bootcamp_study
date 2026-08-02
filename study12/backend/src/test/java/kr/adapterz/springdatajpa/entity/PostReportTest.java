package kr.adapterz.springdatajpa.entity;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

class PostReportTest {

    @Test
    void 게시글_신고_이력을_생성하면_게시글_신고자_사유_생성_시간이_저장된다() {
        User writer = new User(
                "writer@test.com",
                "Password1!",
                "writer",
                "writer.png",
                0
        );

        User reporter = new User(
                "reporter@test.com",
                "Password1!",
                "reporter",
                "reporter.png",
                0
        );

        Post post = new Post(writer, "title", "content");

        PostReport postReport =
                new PostReport(post, reporter);

        assertAll(
                () -> assertThat(postReport.getPost()).isEqualTo(post),
                () -> assertThat(postReport.getUser()).isEqualTo(reporter),
                () -> assertThat(postReport.getCreatedAt()).isNotNull()
        );
    }
}