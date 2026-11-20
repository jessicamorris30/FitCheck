package com.fitcheck.security;

import com.fitcheck.common.exception.ApiError;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.json.JsonMapper;

/**
 * Limits how often one IP address may call the sensitive public endpoints. Runs before anything
 * else touches the request, so a flood is turned away cheaply (no database, no BCrypt).
 *
 * <p>Answers 429 Too Many Requests with a {@code Retry-After} header (seconds), the standard way
 * to say "slow down, try again in N seconds".
 *
 * <p>Like JwtAuthenticationFilter, deliberately not a @Component: SecurityConfig adds it to the
 * security filter chain, so it doesn't also run as a separate servlet filter.
 */
public class RateLimitFilter extends OncePerRequestFilter {

    /** Per-IP limits for POST requests to these paths. Generous for people, tight for scripts. */
    record Rule(int limit, Duration period) {
    }

    static final Map<String, Rule> RULES = Map.of(
            "/api/auth/login", new Rule(10, Duration.ofMinutes(1)),
            "/api/auth/register", new Rule(10, Duration.ofHours(1)),
            "/api/auth/forgot-password", new Rule(5, Duration.ofMinutes(15)),
            "/api/auth/reset-password", new Rule(10, Duration.ofMinutes(15)),
            "/api/auth/verify-email", new Rule(20, Duration.ofMinutes(15)),
            "/api/auth/refresh", new Rule(60, Duration.ofMinutes(1)));

    private final RateLimiter limiter;
    private final JsonMapper jsonMapper;
    private final boolean enabled;

    public RateLimitFilter(RateLimiter limiter, JsonMapper jsonMapper, boolean enabled) {
        this.limiter = limiter;
        this.jsonMapper = jsonMapper;
        this.enabled = enabled;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Rule rule = enabled && "POST".equals(request.getMethod()) ? RULES.get(request.getRequestURI()) : null;
        if (rule != null) {
            // getRemoteAddr() is the caller's IP. Behind Azure's proxy it would be the proxy's IP,
            // so production must trust the X-Forwarded-For header (set up in step 5.6).
            String key = request.getRequestURI() + ":" + request.getRemoteAddr();
            RateLimiter.Decision decision = limiter.tryAcquire(key, rule.limit(), rule.period());
            if (!decision.allowed()) {
                reject(response, decision.retryAfterSeconds());
                return;
            }
        }
        chain.doFilter(request, response);
    }

    private void reject(HttpServletResponse response, long retryAfterSeconds) throws IOException {
        HttpStatus status = HttpStatus.TOO_MANY_REQUESTS;
        response.setStatus(status.value());
        response.setHeader("Retry-After", String.valueOf(retryAfterSeconds));
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        jsonMapper.writeValue(response.getOutputStream(), ApiError.of(status.value(), status.getReasonPhrase(),
                "Too many attempts. Please wait " + humanize(retryAfterSeconds) + " and try again."));
    }

    static String humanize(long seconds) {
        if (seconds < 60) {
            return seconds + " second" + (seconds == 1 ? "" : "s");
        }
        long minutes = (seconds + 59) / 60;
        return minutes + " minute" + (minutes == 1 ? "" : "s");
    }
}
