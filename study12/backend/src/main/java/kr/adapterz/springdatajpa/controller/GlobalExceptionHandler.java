package kr.adapterz.springdatajpa.controller;

import jakarta.persistence.LockTimeoutException;
import jakarta.persistence.PessimisticLockException;
import kr.adapterz.springdatajpa.dto.ErrorResponseDto;
import kr.adapterz.springdatajpa.exception.DataNullException;
import kr.adapterz.springdatajpa.exception.InvalidRequestException;
import kr.adapterz.springdatajpa.exception.LoginFailedException;
import kr.adapterz.springdatajpa.exception.AuthException;
import kr.adapterz.springdatajpa.exception.PostVersionConflictException;
import kr.adapterz.springdatajpa.exception.CounterUpdateException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import kr.adapterz.springdatajpa.exception.ForbiddenException;
import kr.adapterz.springdatajpa.exception.ApiErrorCode;
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(LoginFailedException.class)
    public ResponseEntity<ErrorResponseDto> handleLoginFailedException(LoginFailedException e) {
        return error(HttpStatus.UNAUTHORIZED, e.getMessage());
    }
    @ExceptionHandler(InvalidRequestException.class)
    public ResponseEntity<ErrorResponseDto> handleInvalidRequestException(InvalidRequestException e) {
        return error(HttpStatus.BAD_REQUEST, e.getMessage());
    }
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponseDto> handleValidationException(MethodArgumentNotValidException e) {
        return error(HttpStatus.BAD_REQUEST, "INVALID_REQUEST");
    }
    @ExceptionHandler(AuthException.class)
    public ResponseEntity<ErrorResponseDto> handleAuthException(AuthException e){
        return error(HttpStatus.UNAUTHORIZED, e.getMessage());
    }
    @ExceptionHandler(DataNullException.class)
    public ResponseEntity<ErrorResponseDto> handleDataNullException(DataNullException e){
        return error(HttpStatus.NOT_FOUND, e.getMessage());
    }

    @ExceptionHandler(ForbiddenException.class)
    public ResponseEntity<ErrorResponseDto> handleForbiddenException(
            ForbiddenException e
    ) {
        return error(HttpStatus.FORBIDDEN, e.getMessage());
    }

    @ExceptionHandler({
            PostVersionConflictException.class,
            ObjectOptimisticLockingFailureException.class
    })
    public ResponseEntity<ErrorResponseDto> handlePostVersionConflict(
            RuntimeException e
    ) {
        return error(HttpStatus.CONFLICT, "POST_VERSION_CONFLICT");
    }

    @ExceptionHandler(CounterUpdateException.class)
    public ResponseEntity<ErrorResponseDto> handleCounterUpdateException(
            CounterUpdateException e
    ) {
        return error(HttpStatus.INTERNAL_SERVER_ERROR, e.getMessage());
    }

    @ExceptionHandler({
            PessimisticLockingFailureException.class,
            PessimisticLockException.class,
            LockTimeoutException.class
    })
    public ResponseEntity<ErrorResponseDto> handlePessimisticLockFailure(
            RuntimeException e
    ) {
        return error(HttpStatus.SERVICE_UNAVAILABLE, "COUNTER_UPDATE_FAILED");
    }

    private ResponseEntity<ErrorResponseDto> error(HttpStatus status, String rawCode) {
        ApiErrorCode errorCode = ApiErrorCode.from(rawCode);
        return ResponseEntity.status(status).body(new ErrorResponseDto(
                errorCode.getCode(), errorCode.getMessage(), status.value()
        ));
    }
}
