package kr.adapterz.springdatajpa.controller;

import kr.adapterz.springdatajpa.dto.ErrorResponseDto;
import kr.adapterz.springdatajpa.exception.DataNullException;
import kr.adapterz.springdatajpa.exception.InvalidRequestException;
import kr.adapterz.springdatajpa.exception.LoginFailedException;
import kr.adapterz.springdatajpa.exception.AuthException;
import kr.adapterz.springdatajpa.exception.PostVersionConflictException;
import kr.adapterz.springdatajpa.exception.CounterUpdateException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import kr.adapterz.springdatajpa.exception.ForbiddenException;
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(LoginFailedException.class)
    public ResponseEntity<ErrorResponseDto> handleLoginFailedException(LoginFailedException e) {
        ErrorResponseDto response = new ErrorResponseDto(e.getMessage());

        return ResponseEntity
                .status(HttpStatus.UNAUTHORIZED)
                .body(response);
    }
    @ExceptionHandler(InvalidRequestException.class)
    public ResponseEntity<ErrorResponseDto> handleInvalidRequestException(InvalidRequestException e) {
        ErrorResponseDto response = new ErrorResponseDto(e.getMessage());

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(response);
    }
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponseDto> handleValidationException(MethodArgumentNotValidException e) {
        ErrorResponseDto response = new ErrorResponseDto("invalid_request");

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(response);
    }
    @ExceptionHandler(AuthException.class)
    public ResponseEntity<ErrorResponseDto> handleAuthException(AuthException e){
        ErrorResponseDto response = new ErrorResponseDto(e.getMessage());
        return ResponseEntity
                .status(HttpStatus.UNAUTHORIZED)
                .body(response);
    }
    @ExceptionHandler(DataNullException.class)
    public ResponseEntity<ErrorResponseDto> handleDataNullException(DataNullException e){
        ErrorResponseDto response = new ErrorResponseDto((e.getMessage()));
        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(response);
    }

    @ExceptionHandler(ForbiddenException.class)
    public ResponseEntity<ErrorResponseDto> handleForbiddenException(
            ForbiddenException e
    ) {
        ErrorResponseDto response =
                new ErrorResponseDto(e.getMessage());

        return ResponseEntity
                .status(HttpStatus.FORBIDDEN)
                .body(response);
    }

    @ExceptionHandler({
            PostVersionConflictException.class,
            ObjectOptimisticLockingFailureException.class
    })
    public ResponseEntity<ErrorResponseDto> handlePostVersionConflict(
            RuntimeException e
    ) {
        ErrorResponseDto response =
                new ErrorResponseDto("Post_Version_Conflict");

        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(response);
    }

    @ExceptionHandler(CounterUpdateException.class)
    public ResponseEntity<ErrorResponseDto> handleCounterUpdateException(
            CounterUpdateException e
    ) {
        ErrorResponseDto response =
                new ErrorResponseDto(e.getMessage());

        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(response);
    }
}
