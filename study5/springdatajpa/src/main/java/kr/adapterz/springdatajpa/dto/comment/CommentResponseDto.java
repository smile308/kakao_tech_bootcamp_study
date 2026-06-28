package kr.adapterz.springdatajpa.dto.comment;

import kr.adapterz.springdatajpa.entity.Comment;
import kr.adapterz.springdatajpa.entity.User;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class CommentResponseDto {
    private Long commentId;
    private String content;
    private String userName;
    private String userProfileImage;
    private Long userId;

    public CommentResponseDto(Comment comment, User user) {
        this.commentId = comment.getCommentId();
        this.content = comment.getCommentContent();
        this.userName = user.getNickname();
        this.userProfileImage = user.getProfileImage();
        this.userId=user.getUserId();
    }
}
