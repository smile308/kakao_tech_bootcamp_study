package kr.adapterz.springdatajpa.dto;

import lombok.Getter;

@Getter
public class ErrorResponseDto {

    private String code;
    private String message;
    private int status;

    public ErrorResponseDto(String code, String message, int status) {
        this.code = code;
        this.message = message;
        this.status = status;
    }
}
