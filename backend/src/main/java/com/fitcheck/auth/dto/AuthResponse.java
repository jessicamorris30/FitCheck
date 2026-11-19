package com.fitcheck.auth.dto;

import com.fitcheck.user.dto.UserResponse;

/**
 * Returned after a successful login (and, from step 5, register and refresh).
 *
 * <p>The refresh token is NOT in this body; step 5 puts it in an HttpOnly cookie.
 *
 * @param accessToken the JWT to send as {@code Authorization: Bearer <accessToken>}
 * @param expiresIn   seconds until the access token expires
 * @param user        the logged-in user
 */
public record AuthResponse(String accessToken, long expiresIn, UserResponse user) {
}
