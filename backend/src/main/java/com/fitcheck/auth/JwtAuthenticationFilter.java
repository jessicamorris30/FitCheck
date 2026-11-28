package com.fitcheck.auth;

import com.fitcheck.admin.FrozenAccounts;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Runs on every request, before the authorization rules in SecurityConfig.
 *
 * <p>If the request has {@code Authorization: Bearer <token>} and the token is valid, it records
 * the user in Spring Security's context, so the request counts as logged in. If the header is
 * missing or the token is bad, it does nothing: the request continues as anonymous and protected
 * endpoints answer 401.
 *
 * <p>Deliberately not a @Component/@Bean: Spring Boot would also register it as a plain servlet
 * filter and it would run twice. SecurityConfig creates it and adds it to the security chain.
 */
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtService jwtService;
    private final FrozenAccounts frozenAccounts;

    public JwtAuthenticationFilter(JwtService jwtService, FrozenAccounts frozenAccounts) {
        this.jwtService = jwtService;
        this.frozenAccounts = frozenAccounts;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);

        if (header != null && header.startsWith(BEARER_PREFIX)) {
            String token = header.substring(BEARER_PREFIX.length()).trim();
            jwtService.parseAccessToken(token)
                    // A frozen account's token is ignored straight away (not after it expires), so the
                    // request continues as "not logged in" and protected endpoints answer 401.
                    .filter(user -> !frozenAccounts.isFrozen(user.id()))
                    .ifPresent(user -> {
                // No password (null) and no roles (empty list): FitCheck has only regular users for now.
                var authentication = new UsernamePasswordAuthenticationToken(user, null, List.of());
                SecurityContextHolder.getContext().setAuthentication(authentication);
            });
        }

        chain.doFilter(request, response);
    }
}
