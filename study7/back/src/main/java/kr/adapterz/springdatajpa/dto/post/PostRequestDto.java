package kr.adapterz.springdatajpa.dto.post;


import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class PostRequestDto {
    @NotBlank
    @Size(max=26)
    private String title;
    @NotBlank
    private String contents;
    private String imageFile;
}
