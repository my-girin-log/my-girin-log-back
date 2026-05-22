package com.mygrinlog.common.web;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 프론트가 /api/v1 을 호출할 수 있도록 CORS 허용.
 *
 *  - allowed-origins         : 정확한 URL 매칭 (예: http://localhost:5173). credentials 와 함께 와일드카드(*) 사용 불가.
 *  - allowed-origin-patterns : 패턴 매칭 (예: https://*.vercel.app). credentials 와 함께 사용 가능.
 *
 *  두 환경변수가 다 비어 있지 않은 한 OPTIONS preflight 가 403 나지 않게 둘 다 등록.
 */
@Configuration
public class CorsConfig implements WebMvcConfigurer {

    private final String[] allowedOrigins;
    private final String[] allowedOriginPatterns;

    public CorsConfig(
            @Value("${wootegotchi.cors.allowed-origins:http://localhost:5173,http://127.0.0.1:5173}") String origins,
            @Value("${wootegotchi.cors.allowed-origin-patterns:}") String patterns) {
        this.allowedOrigins = splitNonBlank(origins);
        this.allowedOriginPatterns = splitNonBlank(patterns);
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        var mapping = registry.addMapping("/api/**")
                .allowedMethods("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS")
                .allowedHeaders("*")
                .exposedHeaders("Authorization")
                .allowCredentials(true)
                .maxAge(3600);
        if (allowedOrigins.length > 0) mapping.allowedOrigins(allowedOrigins);
        if (allowedOriginPatterns.length > 0) mapping.allowedOriginPatterns(allowedOriginPatterns);
    }

    private static String[] splitNonBlank(String raw) {
        if (raw == null || raw.isBlank()) return new String[0];
        return java.util.Arrays.stream(raw.split(","))
                .map(String::trim)
                .filter(s -> !s.isBlank())
                .toArray(String[]::new);
    }
}
