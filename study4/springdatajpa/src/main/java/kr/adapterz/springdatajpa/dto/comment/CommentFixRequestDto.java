package kr.adapterz.springdatajpa.dto.comment;

import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class CommentFixRequestDto {
    private Long user_id;
    private Long post_id;
    private Long comment_id;
    private String comment_content;
}
