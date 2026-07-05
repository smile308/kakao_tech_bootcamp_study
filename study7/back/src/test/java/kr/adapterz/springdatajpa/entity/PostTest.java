package kr.adapterz.springdatajpa.entity;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

class PostTest {

    private User createUser() {
        return new User("test@test.com", "Password1!", "tester", "profile.png");
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
                () -> assertThat(post.isDeleted()).isFalse()
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
        post.likeCancle();
        post.view();
        post.delete();

        assertAll(
                () -> assertThat(post.getReplyCount()).isZero(),
                () -> assertThat(post.getLikeCount()).isZero(),
                () -> assertThat(post.getViewCount()).isEqualTo(1),
                () -> assertThat(post.isDeleted()).isTrue()
        );
    }
}