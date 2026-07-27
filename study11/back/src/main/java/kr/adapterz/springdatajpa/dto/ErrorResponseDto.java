package kr.adapterz.springdatajpa.dto;

import lombok.Getter;

@Getter
public class ErrorResponseDto {

    private String message;

    public ErrorResponseDto(String message) {
        this.message = message;
    }
}