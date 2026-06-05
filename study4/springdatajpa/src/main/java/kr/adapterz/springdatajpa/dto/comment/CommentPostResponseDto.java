package kr.adapterz.springdatajpa.dto.comment;

import kr.adapterz.springdatajpa.entity.Comment;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class CommentPostResponseDto {
    private String message="post_success";
}
