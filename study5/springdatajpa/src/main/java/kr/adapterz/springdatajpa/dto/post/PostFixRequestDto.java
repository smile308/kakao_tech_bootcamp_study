package kr.adapterz.springdatajpa.dto.post;

import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class PostFixRequestDto {
    private Long userId;
    @Size(max=26)
    private String title;
    private String contents;
    private String imageFile;
}
