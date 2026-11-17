package com.precisionnutrition.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Configuração global de CORS para permitir consumo seguro pelo front-end Next.js (Web & Mobile PWA).
 */
@Configuration
public class CorsConfig implements WebMvcConfigurer {

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
                .allowedOriginPatterns(
                        "http://localhost:3000",
                        "http://127.0.0.1:3000",
                        "http://localhost:[*]",
                        "http://127.0.0.1:[*]",
                        "http://192.168.*.*:[*]",
                        "http://10.*.*.*:[*]"
                )
                .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .exposedHeaders("Authorization", "Link", "X-Total-Count")
                .allowCredentials(true)
                .maxAge(3600);
    }
}
