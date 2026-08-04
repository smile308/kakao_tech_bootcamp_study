package kr.adapterz.springdatajpa.entity;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

class PostTest {

    private User createUser() {
        return new User("test@test.com", "Password1!", "tester", "profile.png",0);
    }

    @Test
    void 게시글_생성_시_기본값이_정상_설정된다() {
        User user = createUser();

        Post post = new Post(user, "title", "content");

        assertAll(
                () -> assertThat(post.getUser()).isEqualTo(user),
                () -> assertThat(post.getPostTitle()).isEqualTo("title"),
                () -> assertThat(post.getPostContent()).isEqualTo("content"),
                () -> assertThat(post.getImageFile()).isNull(),
                () -> assertThat(post.getPostImages()).isEmpty(),
                () -> assertThat(post.isFixed()).isFalse(),
                () -> assertThat(post.getPostCounter()).isNotNull(),
                () -> assertThat(post.getPostCounter().getPost()).isEqualTo(post),
                () -> assertThat(post.getPostViewCount()).isNotNull(),
                () -> assertThat(post.getPostViewCount().getPost()).isEqualTo(post),
                () -> assertThat(post.getPostViewCount().getViewCount()).isZero(),
                () -> assertThat(post.getLikeCount()).isZero(),
                () -> assertThat(post.getReplyCount()).isZero(),
                () -> assertThat(post.getCreatedAt()).isNotNull(),
                () -> assertThat(post.isDeleted()).isFalse(),
                () -> assertThat(post.getReportCount()).isZero()
        );
    }

    @Test
    void 이미지가_있는_게시글을_생성하면_이미지가_저장된다() {
        Post post = new Post(createUser(), "title", "content", "image.png");

        assertAll(
                () -> assertThat(post.getImageFile()).isEqualTo("image.png"),
                () -> assertThat(post.getPostImages()).hasSize(1),
                () -> assertThat(post.getPostImages().get(0).getPost()).isEqualTo(post),
                () -> assertThat(post.getPostImages().get(0).getImageOrder()).isZero()
        );
    }

    @Test
    void 게시글_수정_시_제목_내용_이미지가_변경되고_수정_상태가_된다() {
        Post post = new Post(createUser(), "old", "old content", "old.png");

        post.update("new", "new content", "new.png");

        assertAll(
                () -> assertThat(post.getPostTitle()).isEqualTo("new"),
                () -> assertThat(post.getPostContent()).isEqualTo("new content"),
                () -> assertThat(post.getImageFile()).isEqualTo("new.png"),
                () -> assertThat(post.isFixed()).isTrue()
        );
    }

    @Test
    void 게시글_카운트와_삭제_상태가_정상_변경된다() {
        Post post = new Post(createUser(), "title", "content");

        post.addReply();
        post.deleteReply();
        post.like();
        post.delete();
        post.report();

        assertAll(
                () -> assertThat(post.getReplyCount()).isZero(),
                () -> assertThat(post.getLikeCount()).isEqualTo(1),
                () -> assertThat(post.getReportCount()).isEqualTo(1),
                () -> assertThat(post.isDeleted()).isTrue()
        );
        post.likeCancle();
        assertThat(post.getLikeCount()).isEqualTo(0);
    }

    @Test
    void 이미지를_null이나_공백으로_교체하면_이미지_목록이_비워진다() {
        Post post = new Post(createUser(), "title", "content", "old.png");

        post.replaceImages((String) null);

        assertThat(post.getPostImages()).isEmpty();
        assertThat(post.getImageFile()).isNull();

        post.replaceImages("   ");

        assertThat(post.getPostImages()).isEmpty();
        assertThat(post.getImageFile()).isNull();
    }

    @Test
    void 여러_이미지_교체_시_null과_공백_이미지는_제외된다() {
        Post post = new Post(createUser(), "title", "content", "old.png");

        post.replaceImages(Arrays.asList("first.png", null, " ", "second.png"));

        assertAll(
                () -> assertThat(post.getPostImages()).hasSize(2),
                () -> assertThat(post.getPostImages().get(0).getImageFile()).isEqualTo("first.png"),
                () -> assertThat(post.getPostImages().get(0).getImageOrder()).isZero(),
                () -> assertThat(post.getPostImages().get(1).getImageFile()).isEqualTo("second.png"),
                () -> assertThat(post.getPostImages().get(1).getImageOrder()).isEqualTo(3),
                () -> assertThat(post.getImageFile()).isEqualTo("first.png")
        );
    }

    @Test
    void 여러_이미지_교체_시_이미지_목록이_null이면_목록이_비워진다() {
        Post post = new Post(createUser(), "title", "content", "old.png");

        post.replaceImages((List<String>) null);

        assertThat(post.getPostImages()).isEmpty();
    }

    @Test
    void 여러_이미지로_게시글을_수정하면_제목과_내용_이미지_목록이_변경된다() {
        Post post = new Post(createUser(), "old", "old content", "old.png");

        post.update("new", "new content", List.of("first.png", "second.png"));

        assertAll(
                () -> assertThat(post.getPostTitle()).isEqualTo("new"),
                () -> assertThat(post.getPostContent()).isEqualTo("new content"),
                () -> assertThat(post.getPostImages()).hasSize(2),
                () -> assertThat(post.getImageFile()).isEqualTo("first.png"),
                () -> assertThat(post.isFixed()).isTrue()
        );
    }
}
