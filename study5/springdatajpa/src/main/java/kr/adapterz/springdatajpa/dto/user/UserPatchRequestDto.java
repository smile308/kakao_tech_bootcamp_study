package kr.adapterz.springdatajpa.dto.user;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class UserPatchRequestDto {
    private long user_id;
    private String access_session;
    @NotBlank
    @Size(max = 10)
    //공백 제거 조건
    @Pattern(regexp = "^\\S+$")
    private String nickname;
    private String profile_image;
}
