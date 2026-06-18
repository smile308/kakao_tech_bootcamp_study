package kr.adapterz.springdatajpa.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class UserInfoDto {
    private final Long id;
    private final String email;
    private final String nickname;
}