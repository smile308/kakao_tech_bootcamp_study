package kr.adapterz.springdatajpa.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.io.IOException;

@Component
@RequiredArgsConstructor
public class SessionOriginInterceptor implements HandlerInterceptor {

    private final CorsOriginProvider corsOriginProvider;

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

        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write("{\"message\":\"Forbidden_Origin\"}");
        return false;
    }

    private boolean isCookieSessionRequest(HttpServletRequest request) {
        String method = request.getMethod();
        String path = request.getServletPath();

        return method.equals("POST") && path.equals("/sessions")
                || method.equals("POST") && path.equals("/sessions/refresh")
                || method.equals("DELETE") && path.equals("/sessions");
    }
}
