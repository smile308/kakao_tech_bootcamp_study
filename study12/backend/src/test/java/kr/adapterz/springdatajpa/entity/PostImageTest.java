package kr.adapterz.springdatajpa.entity;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

class PostImageTest {

    @Test
    void 게시글_이미지_생성_시_게시글_파일명_순서가_설정된다() {
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
