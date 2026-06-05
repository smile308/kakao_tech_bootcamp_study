package kr.adapterz.springdatajpa.dto.post;

import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class PostFixRequestDto {
    private Long user_id;
    private String access_session;
    private Long post_id;
    @Size(max=26)
    private String title;
    private String contents;
    private String image_file;
}
