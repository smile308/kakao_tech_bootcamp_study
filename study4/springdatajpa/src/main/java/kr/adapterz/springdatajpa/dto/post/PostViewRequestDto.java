package kr.adapterz.springdatajpa.dto.post;

import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class PostViewRequestDto {
    private Long user_id;
    private Long post_id;
}
