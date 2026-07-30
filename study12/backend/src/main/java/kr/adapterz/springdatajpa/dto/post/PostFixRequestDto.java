package kr.adapterz.springdatajpa.dto.post;

import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

@Getter
@NoArgsConstructor
public class PostFixRequestDto {
    @NotNull
    private Long version;

    @Size(max = 26)
    private String title;
    private String content;
    private List<String> imageFiles;
}
