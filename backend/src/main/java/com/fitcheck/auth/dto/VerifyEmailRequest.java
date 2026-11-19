package com.fitcheck.auth.dto;

import jakarta.validation.constraints.NotBlank;

/** JSON body of {@code POST /api/auth/verify-email}: the token from the email link. */
public record VerifyEmailRequest(@NotBlank(message = "Token is required") String token) {
}
