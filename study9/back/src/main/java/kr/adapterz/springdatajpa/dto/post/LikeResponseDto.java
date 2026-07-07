package kr.adapterz.springdatajpa.dto.post;

import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class LikeResponseDto {
    private String message = "like_success";
    private int likeCount;
    public LikeResponseDto(int likeCount){
        this.likeCount=likeCount;
    }
}
