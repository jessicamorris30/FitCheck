package com.fitcheck.auth.dto;

import com.fitcheck.user.dto.UserResponse;

/**
 * Returned by register/login/refresh. The refresh token is NOT in the body; it is set as an HttpOnly cookie.
 */
public record AuthResponse(String accessToken, long expiresIn, UserResponse user) {
}
