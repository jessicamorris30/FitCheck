package com.fitcheck.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Body of {@code POST /api/auth/reset-password}: the token from the email link and the new password. */
public record ResetPasswordRequest(
        @NotBlank(message = "The reset link is missing its token")
        String token,

        @NotBlank(message = "Enter a new password")
        @Size(min = 8, max = 72, message = "Password must be 8-72 characters")
        String newPassword) {
}
