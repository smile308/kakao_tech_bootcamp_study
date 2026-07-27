package kr.adapterz.springdatajpa.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class CorsOriginProvider {

    private final Set<String> allowedOrigins;

    public CorsOriginProvider(
            @Value("${cors.allowed-origins}") String allowedOrigins
    ) {
        this.allowedOrigins = Arrays.stream(allowedOrigins.split(","))
                .map(String::trim)
                .filter(origin -> !origin.isEmpty())
                .collect(Collectors.toUnmodifiableSet());
    }

    public String[] getAllowedOrigins() {
        return allowedOrigins.toArray(String[]::new);
    }

    public boolean isAllowed(String origin) {
        return origin == null || allowedOrigins.contains(origin);
    }
}
