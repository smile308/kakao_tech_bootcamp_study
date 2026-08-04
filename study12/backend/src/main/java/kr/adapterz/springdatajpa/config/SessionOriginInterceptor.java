package kr.adapterz.springdatajpa.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import kr.adapterz.springdatajpa.exception.ApiErrorCode;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.io.IOException;

@Component
@RequiredArgsConstructor
public class SessionOriginInterceptor implements HandlerInterceptor {

    private final CorsOriginProvider corsOriginProvider;
    private final ErrorResponseWriter errorResponseWriter;

    @Override
    public boolean preHandle(
            HttpServletRequest request,
            HttpServletResponse response,
            Object handler
    ) throws IOException {
        if (!isCookieSessionRequest(request)) {
            return true;
        }

        String origin = request.getHeader("Origin");

        if (corsOriginProvider.isAllowed(origin)) {
            return true;
        }

        errorResponseWriter.write(response, org.springframework.http.HttpStatus.FORBIDDEN, ApiErrorCode.FORBIDDEN_ORIGIN);
        return false;
    }

    private boolean isCookieSessionRequest(HttpServletRequest request) {
        String method = request.getMethod();
        String path = request.getRequestURI();

        return method.equals("POST") && path.equals("/sessions")
                || method.equals("POST") && path.equals("/sessions/refresh")
                || method.equals("DELETE") && path.equals("/sessions");
    }
}
