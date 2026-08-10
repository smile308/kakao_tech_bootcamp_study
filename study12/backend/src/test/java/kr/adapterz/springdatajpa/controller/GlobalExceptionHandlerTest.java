// 전역 예외 처리기의 동시성 실패 응답을 검증하는 테스트
package kr.adapterz.springdatajpa.controller;

import kr.adapterz.springdatajpa.dto.ErrorResponseDto;
import org.junit.jupiter.api.Test;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {

    @Test
    void 비관적_락_획득_실패는_재시도_가능한_통계_오류로_응답한다() {
        GlobalExceptionHandler handler = new GlobalExceptionHandler();

        ResponseEntity<ErrorResponseDto> response =
                handler.handlePessimisticLockFailure(
                        new CannotAcquireLockException("deadlock")
                );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getCode()).isEqualTo("COUNTER_UPDATE_FAILED");
        assertThat(response.getBody().getStatus()).isEqualTo(503);
    }
}
