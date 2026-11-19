package com.fitcheck.config;

import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/**
 * CORS ("Cross-Origin Resource Sharing") tells browsers which other websites may call this API.
 *
 * <p>In local dev the Vite proxy makes the frontend and API look like the same site, so CORS
 * rarely kicks in. It matters once the frontend is deployed on its own domain.
 * Allowed origins come from {@code fitcheck.cors.allowed-origins} (CORS_ALLOWED_ORIGINS in .env),
 * comma-separated.
 */
@Configuration
public class CorsConfig {

    @Bean
    public CorsConfigurationSource corsConfigurationSource(
            @Value("${fitcheck.cors.allowed-origins}") List<String> allowedOrigins) {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(allowedOrigins);
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("Authorization", "Content-Type"));
        // Required so the browser sends the refresh-token cookie (step 5) on cross-site requests.
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", config);
        return source;
    }
}
