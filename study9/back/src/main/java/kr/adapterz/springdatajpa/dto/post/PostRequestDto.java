package kr.adapterz.springdatajpa.dto.post;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

@Getter
@NoArgsConstructor
public class PostRequestDto {
    @NotBlank
    @Size(max = 26)
    private String title;
    @NotBlank
    private String content;
    private List<String> imageFiles;
}
