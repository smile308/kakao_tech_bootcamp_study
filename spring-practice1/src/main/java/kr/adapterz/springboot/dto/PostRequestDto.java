package kr.adapterz.springboot.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class PostRequestDto {

    @NotBlank
    private String title;

    @NotBlank
    private String content;

}