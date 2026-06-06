package kr.adapterz.springdatajpa.controller;

import kr.adapterz.springdatajpa.dto.ErrorResponseDto;
import kr.adapterz.springdatajpa.exception.DataNullException;
import kr.adapterz.springdatajpa.exception.InvalidRequestException;
import kr.adapterz.springdatajpa.exception.LoginFailedException;
import kr.adapterz.springdatajpa.exception.AuthException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
//전역 예외 처리기
@RestControllerAdvice
public class GlobalExceptionHandler {

    //로그인 오류
    @ExceptionHandler(LoginFailedException.class)
    public ResponseEntity<ErrorResponseDto> handleLoginFailedException(LoginFailedException e) {
        ErrorResponseDto response = new ErrorResponseDto(e.getMessage());

        //로그인 오류이기에 401 오류 반환
        return ResponseEntity
                .status(HttpStatus.UNAUTHORIZED)
                .body(response);
    }
    //잘못된 요청 오류
    @ExceptionHandler(InvalidRequestException.class)
    public ResponseEntity<ErrorResponseDto> handleInvalidRequestException(InvalidRequestException e) {
        ErrorResponseDto response = new ErrorResponseDto(e.getMessage());

        //400 오류 반환
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(response);
    }
    //Valid 검증 실패 시 발생하는 오류
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponseDto> handleValidationException(MethodArgumentNotValidException e) {
        ErrorResponseDto response = new ErrorResponseDto("invalid_request");

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(response);
    }
    //Session 검증 오류
    @ExceptionHandler(AuthException.class)
    public ResponseEntity<ErrorResponseDto> handleAuthException(jakarta.security.auth.message.AuthException e){
        ErrorResponseDto response = new ErrorResponseDto(e.getMessage());
        return ResponseEntity
                .status(HttpStatus.FORBIDDEN)
                .body(response);
    }
    //데이터가 없을 경우
    @ExceptionHandler(DataNullException.class)
    public ResponseEntity<ErrorResponseDto> handleDataNullException(DataNullException e){
        ErrorResponseDto response = new ErrorResponseDto((e.getMessage()));
        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(response);
    }
}