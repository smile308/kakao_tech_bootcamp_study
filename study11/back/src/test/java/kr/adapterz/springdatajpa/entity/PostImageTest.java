package kr.adapterz.springdatajpa.entity;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

class PostImageTest {

    @Test
    @DisplayName("게시글 이미지 생성 시 게시글, 파일명, 순서가 설정된다")
    void createPostImage() {
        User user = new User("test@test.com", "Password1!", "tester",0);
        Post post = new Post(user, "title", "content");

        PostImage postImage = new PostImage(post, "image.png", 3);

        assertAll(
                () -> assertThat(postImage.getPost()).isEqualTo(post),
                () -> assertThat(postImage.getImageFile()).isEqualTo("image.png"),
                () -> assertThat(postImage.getImageOrder()).isEqualTo(3)
        );
    }
}
