package kr.adapterz.springboot.exception;

import org.springframework.http.HttpStatus;

public class NotFoundException extends BusinessException {
    public NotFoundException(String code) {
        super(code, HttpStatus.NOT_FOUND);
    }
}