package kr.adapterz.springdatajpa.dto.user;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class SessionRequestDto {
    @NotBlank
    @Email
    private String email;

    @NotBlank
    //특수문자,대문자,소문자,숫자 각각 최소 1개 포함하며 8~20자
    @Pattern(
            regexp = "^(?=.*[A-Z])(?=.*[a-z])(?=.*\\d)(?=.*[!@#$%^&*()_+\\-=\\[\\]{};':\"\\\\|,.<>\\/?~]).{8,20}$"
    )
    private String password;
}
