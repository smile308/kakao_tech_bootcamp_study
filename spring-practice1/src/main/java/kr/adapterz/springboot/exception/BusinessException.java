package kr.adapterz.springboot.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public class BusinessException extends RuntimeException {

    private final String code;
    private final HttpStatus status;

    public BusinessException(String code, HttpStatus status) {
        super(code);
        this.code = code;
        this.status = status;
    }
}