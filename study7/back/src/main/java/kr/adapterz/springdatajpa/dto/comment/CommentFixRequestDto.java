package kr.adapterz.springdatajpa.dto.comment;

import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class CommentFixRequestDto {
    private Long userId;
    private Long commentId;
    private String commentContent;
}
