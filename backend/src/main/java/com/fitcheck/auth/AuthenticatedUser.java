package com.fitcheck.auth;

import java.util.UUID;

/**
 * Who is making the current request, as proven by their access token.
 *
 * <p>JwtAuthenticationFilter puts this into Spring Security's context. Controllers get it with
 * {@code @AuthenticationPrincipal AuthenticatedUser me}.
 */
public record AuthenticatedUser(UUID id, String email) {
}
