package kr.adapterz.springdatajpa.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Slf4j
@Component
public class RequestLogInterceptor implements HandlerInterceptor {

    private static final String START_TIME = "startTime";

    @Override
    public boolean preHandle(
            HttpServletRequest request,
            HttpServletResponse response,
            Object handler
    ) {
        request.setAttribute(START_TIME, System.currentTimeMillis());

        log.info("[REQUEST] {} {}", request.getMethod(), request.getRequestURI());

        return true;
    }

    @Override
    public void afterCompletion(
            HttpServletRequest request,
            HttpServletResponse response,
            Object handler,
            Exception ex
    ) {
        Long startTime = (Long) request.getAttribute(START_TIME);
        long elapsedTime = System.currentTimeMillis() - startTime;

        log.info(
                "[RESPONSE] {} {} status={} time={}ms",
                request.getMethod(),
                request.getRequestURI(),
                response.getStatus(),
                elapsedTime
        );
    }
}