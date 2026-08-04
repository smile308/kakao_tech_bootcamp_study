package kr.adapterz.springdatajpa.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import kr.adapterz.springdatajpa.dto.ErrorResponseDto;
import kr.adapterz.springdatajpa.exception.ApiErrorCode;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class ErrorResponseWriter {

    private final ObjectMapper objectMapper = new ObjectMapper();

    public void write(HttpServletResponse response, HttpStatus status, ApiErrorCode errorCode) throws IOException {
        response.setStatus(status.value());
        response.setContentType("application/json;charset=UTF-8");
        objectMapper.writeValue(response.getWriter(), new ErrorResponseDto(
                errorCode.getCode(), errorCode.getMessage(), status.value()
        ));
    }
}
