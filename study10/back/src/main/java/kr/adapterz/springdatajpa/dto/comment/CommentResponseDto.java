package kr.adapterz.springdatajpa.dto.comment;

import kr.adapterz.springdatajpa.entity.Comment;
import kr.adapterz.springdatajpa.entity.User;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Getter
@NoArgsConstructor
public class CommentResponseDto {

    private Long commentId;
    private String content;
    private String authorNickname;
    private String authorProfileImage;
    private Boolean isMine;
    private LocalDateTime createdAt;

    public CommentResponseDto(
            Comment comment,
            User user,
            Boolean isMine
    ) {
        this.commentId = comment.getCommentId();
        this.content = comment.getCommentContent();
        this.authorNickname = user.getNickname();
        this.authorProfileImage = user.getProfileImage();
        this.isMine = isMine;
        this.createdAt = comment.getCreatedAt();
    }
}
