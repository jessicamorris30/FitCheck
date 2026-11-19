package com.fitcheck.auth.dto;

import com.fitcheck.user.dto.UserResponse;

/**
 * Returned by register, login and refresh.
 *
 * <p>The refresh token is NOT in this body; it is set as an HttpOnly cookie.
 *
 * @param accessToken the JWT to send as {@code Authorization: Bearer <accessToken>}
 * @param expiresIn   seconds until the access token expires
 * @param user        the logged-in user
 */
public record AuthResponse(String accessToken, long expiresIn, UserResponse user) {
}
