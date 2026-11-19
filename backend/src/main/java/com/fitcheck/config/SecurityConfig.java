package com.fitcheck.config;

import com.fitcheck.common.exception.ApiError;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import tools.jackson.databind.json.JsonMapper;

/**
 * Who may call what. Every HTTP request passes through Spring Security's filter chain
 * (configured below) before it reaches a controller.
 *
 * <p>FitCheck is a stateless JSON API: the client proves who it is on every request with a
 * JWT in the Authorization header (added in step 4). So:
 * <ul>
 *   <li>no server-side sessions,
 *   <li>no login form or HTTP Basic popup,
 *   <li>CSRF protection off: CSRF attacks abuse cookies the browser attaches automatically,
 *       and our API calls use a header instead (see docs/AUTH.md for the refresh cookie).
 * </ul>
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, JsonMapper jsonMapper) throws Exception {
        http
                .cors(Customizer.withDefaults()) // uses the CorsConfigurationSource bean from CorsConfig
                .csrf(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // Public: sign-up, login, token refresh, and the health check.
                        .requestMatchers("/api/auth/**", "/actuator/health").permitAll()
                        // Spring forwards errors to /error internally; keep it open so real
                        // errors (like 404) aren't hidden behind a 401.
                        .requestMatchers("/error").permitAll()
                        // Everything else requires a logged-in user.
                        .anyRequest().authenticated())
                .exceptionHandling(e -> e
                        // Not logged in → 401 in our ApiError format (instead of a login page).
                        .authenticationEntryPoint((request, response, ex) ->
                                writeError(response, HttpStatus.UNAUTHORIZED, "Authentication required", jsonMapper))
                        // Logged in but not allowed → 403.
                        .accessDeniedHandler((request, response, ex) ->
                                writeError(response, HttpStatus.FORBIDDEN, "Access denied", jsonMapper)));

        return http.build();
    }

    /**
     * BCrypt hashes passwords with a random salt and is deliberately slow, which makes
     * guessing passwords from a leaked database very expensive. Used in step 3.
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    private static void writeError(HttpServletResponse response, HttpStatus status, String message, JsonMapper jsonMapper)
            throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        jsonMapper.writeValue(response.getOutputStream(), ApiError.of(status.value(), status.getReasonPhrase(), message));
    }
}
