package kr.adapterz.springdatajpa.dto.post;


import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Getter
@NoArgsConstructor
public class PostRequestDto {
    @NotBlank
    @Size(max=26)
    private String title;
    @NotBlank
    private String contents;
    private String imageFile;
    private List<String> imageFiles;

    public List<String> getPostImageFiles() {
        if (imageFiles != null) {
            return imageFiles;
        }

        List<String> result = new ArrayList<>();

        if (imageFile != null && !imageFile.isBlank()) {
            result.add(imageFile);
        }

        return result;
    }
}
