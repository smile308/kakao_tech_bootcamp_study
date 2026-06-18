package kr.adapterz.springdatajpa.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class PostSummaryDto {
    private final Long id;
    private final String title;
    private final String authorNickname;
}