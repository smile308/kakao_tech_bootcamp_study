package kr.adapterz.springdatajpa.dto.user;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class UserPatchRequestDto {
    @NotBlank
    @Size(max = 10)
    @Pattern(regexp = "^\\S+$")
    private String nickname;
    private String profileImage;
}
