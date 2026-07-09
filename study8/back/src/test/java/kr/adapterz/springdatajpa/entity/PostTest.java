package kr.adapterz.springdatajpa.entity;

import org.junit.jupiter.api.DisplayName;
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
    @DisplayName("게시글 생성 시 기본값이 정상 설정된다")
    void createPostDefaultValues() {
        User user = createUser();

        Post post = new Post(user, "title", "content");

        assertAll(
                () -> assertThat(post.getUser()).isEqualTo(user),
                () -> assertThat(post.getPostTitle()).isEqualTo("title"),
                () -> assertThat(post.getPostContent()).isEqualTo("content"),
                () -> assertThat(post.getImageFile()).isNull(),
                () -> assertThat(post.getPostImages()).isEmpty(),
                () -> assertThat(post.isFixed()).isFalse(),
                () -> assertThat(post.getLikeCount()).isZero(),
                () -> assertThat(post.getReplyCount()).isZero(),
                () -> assertThat(post.getViewCount()).isZero(),
                () -> assertThat(post.getCreatedAt()).isNotNull(),
                () -> assertThat(post.isDeleted()).isFalse(),
                () -> assertThat(post.getReportCount()).isZero()
        );
    }

    @Test
    @DisplayName("이미지가 있는 게시글을 생성하면 이미지가 저장된다")
    void createPostWithImage() {
        Post post = new Post(createUser(), "title", "content", "image.png");

        assertAll(
                () -> assertThat(post.getImageFile()).isEqualTo("image.png"),
                () -> assertThat(post.getPostImages()).hasSize(1),
                () -> assertThat(post.getPostImages().get(0).getPost()).isEqualTo(post),
                () -> assertThat(post.getPostImages().get(0).getImageOrder()).isZero()
        );
    }

    @Test
    @DisplayName("게시글 수정 시 제목, 내용, 이미지가 변경되고 수정 상태가 된다")
    void updatePost() {
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
    @DisplayName("게시글 카운트와 삭제 상태가 정상 변경된다")
    void updateCountsAndDelete() {
        Post post = new Post(createUser(), "title", "content");

        post.addReply();
        post.deleteReply();
        post.like();
        post.view();
        post.delete();
        post.report();

        assertAll(
                () -> assertThat(post.getReplyCount()).isZero(),
                () -> assertThat(post.getLikeCount()).isEqualTo(1),
                () -> assertThat(post.getReportCount()).isEqualTo(1),
                () -> assertThat(post.getViewCount()).isEqualTo(1),
                () -> assertThat(post.isDeleted()).isTrue()
        );
        post.likeCancle();
        assertThat(post.getLikeCount()).isEqualTo(0);
    }

    @Test
    @DisplayName("이미지를 null이나 공백으로 교체하면 이미지 목록이 비워진다")
    void replaceImagesWithNullOrBlank() {
        Post post = new Post(createUser(), "title", "content", "old.png");

        post.replaceImages((String) null);

        assertThat(post.getPostImages()).isEmpty();
        assertThat(post.getImageFile()).isNull();

        post.replaceImages("   ");

        assertThat(post.getPostImages()).isEmpty();
        assertThat(post.getImageFile()).isNull();
    }

    @Test
    @DisplayName("여러 이미지 교체 시 null과 공백 이미지는 제외된다")
    void replaceImagesWithListSkipsNullAndBlank() {
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
    @DisplayName("여러 이미지 교체 시 이미지 목록이 null이면 목록이 비워진다")
    void replaceImagesWithNullList() {
        Post post = new Post(createUser(), "title", "content", "old.png");

        post.replaceImages((List<String>) null);

        assertThat(post.getPostImages()).isEmpty();
    }

    @Test
    @DisplayName("여러 이미지로 게시글을 수정하면 제목과 내용, 이미지 목록이 변경된다")
    void updatePostWithImageList() {
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
