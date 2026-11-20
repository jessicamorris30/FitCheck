package com.fitcheck.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/** Body of {@code POST /api/auth/forgot-password}. */
public record ForgotPasswordRequest(
        @NotBlank(message = "Enter your email")
        @Email(message = "Must be a valid email address")
        String email) {

    public ForgotPasswordRequest {
        if (email != null) {
            email = email.trim();
        }
    }
}
