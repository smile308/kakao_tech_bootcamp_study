package kr.adapterz.springdatajpa.dto.post;

import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class PostRequestDto {
    private Long user_id;
    private String access_session;
    private String title;
    private String contents;
    private String image_file;
}
