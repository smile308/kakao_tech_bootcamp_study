package kr.adapterz.springdatajpa.dto.post;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class PostDeleteRequestDto {
    @NotNull
    private Long version;
}
