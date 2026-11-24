package com.precisionnutrition.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Configuração global de CORS para permitir consumo seguro pelo front-end Next.js (Web & Mobile PWA).
 */
@Configuration
public class CorsConfig implements WebMvcConfigurer {

    @Value("${CORS_ALLOWED_ORIGIN:https://localhost}")
    private String allowedOrigin;

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
                .allowedOriginPatterns(
                        "http://localhost:3000",
                        "http://127.0.0.1:3000",
                        "http://localhost:[*]",
                        "http://127.0.0.1:[*]",
                        "https://localhost",
                        "https://localhost:[*]",
                        "http://192.168.*.*:[*]",
                        "http://10.*.*.*:[*]",
                        allowedOrigin
                )
                .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .exposedHeaders("Authorization", "Link", "X-Total-Count")
                .allowCredentials(true)
                .maxAge(3600);
    }
}
