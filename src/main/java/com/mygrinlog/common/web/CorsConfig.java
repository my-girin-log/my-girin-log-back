package com.mygrinlog.common.web;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 프론트(Vite dev = http://localhost:5173) 가 /api/v1 호출할 수 있도록 CORS 허용.
 * 운영 origin 은 application.yml 의 wootegotchi.cors.allowed-origins 로 콤마구분 설정.
 */
@Configuration
public class CorsConfig implements WebMvcConfigurer {

    private final String[] allowedOrigins;

    public CorsConfig(@Value("${wootegotchi.cors.allowed-origins:http://localhost:5173,http://127.0.0.1:5173}") String origins) {
        this.allowedOrigins = origins.split(",");
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                // 정확한 origin 리스트 — credentials 와 함께 쓰려면 wildcard 안 됨, 명시.
                // 패턴(와일드카드 포함) 이 필요하면 allowedOriginPatterns 로 교체.
                .allowedOrigins(allowedOrigins)
                .allowedMethods("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS")
                .allowedHeaders("*")
                .exposedHeaders("Authorization")
                .allowCredentials(true)
                .maxAge(3600);
    }
}
