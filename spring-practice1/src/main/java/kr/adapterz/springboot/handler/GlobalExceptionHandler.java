package kr.adapterz.springboot.handler;

import kr.adapterz.springboot.dto.ErrorResponseDto;
import kr.adapterz.springboot.exception.BusinessException;
import kr.adapterz.springboot.exception.NotFoundException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(NotFoundException.class)
    public ErrorResponseDto handleNotFound(
            NotFoundException exception) {

        return ErrorResponseDto.of(exception.getCode());
    }

    @ExceptionHandler(BusinessException.class)
    public ErrorResponseDto handleBusiness(
            BusinessException exception) {

        return ErrorResponseDto.of(exception.getCode());
    }
}