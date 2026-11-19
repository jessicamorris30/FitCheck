package com.fitcheck.auth.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * JSON body of {@code POST /api/auth/login}.
 *
 * <p>Only "not blank" is checked here. Format rules (valid email, password length) belong to
 * registration; at login we just compare against what's stored.
 */
public record LoginRequest(
        @NotBlank(message = "Email is required") String email,
        @NotBlank(message = "Password is required") String password) {

    public LoginRequest {
        if (email != null) {
            email = email.trim();
        }
    }
}
