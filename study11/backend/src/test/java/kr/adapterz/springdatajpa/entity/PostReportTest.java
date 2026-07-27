package kr.adapterz.springdatajpa.entity;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

class PostReportTest {

    @Test
    @DisplayName("게시글 신고 이력을 생성하면 게시글, 신고자, 사유, 생성 시간이 저장된다")
    void createPostReport() {
        // given
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

        // when
        PostReport postReport =
                new PostReport(post, reporter);

        // then
        assertAll(
                () -> assertThat(postReport.getPost()).isEqualTo(post),
                () -> assertThat(postReport.getUser()).isEqualTo(reporter),
                () -> assertThat(postReport.getCreatedAt()).isNotNull()
        );
    }
}