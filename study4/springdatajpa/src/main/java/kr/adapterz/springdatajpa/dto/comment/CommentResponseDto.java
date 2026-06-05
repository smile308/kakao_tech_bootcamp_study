package kr.adapterz.springdatajpa.dto.comment;

import kr.adapterz.springdatajpa.entity.Comment;
import kr.adapterz.springdatajpa.entity.User;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class CommentResponseDto {
    private Long comment_id;
    private String content;
    private String user_name;
    private String user_profile_image;

    public CommentResponseDto(Comment comment, User user) {
        this.comment_id = comment.getComment_id();
        this.content = comment.getComment_content();
        this.user_name = user.getNickname();
        this.user_profile_image = user.getProfile_image();
    }
}
