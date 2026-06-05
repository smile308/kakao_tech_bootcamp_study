package kr.adapterz.springdatajpa.dto.post;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class PostRequestDto {
    private Long user_id;
    private String access_session;
    @NotBlank
    @Size(max=26)
    private String title;
    @NotBlank
    private String contents;
    private String image_file;
}
