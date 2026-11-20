package com.fitcheck.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Body of {@code POST /api/auth/change-password}. Same new-password rules as registration. */
public record ChangePasswordRequest(
        @NotBlank(message = "Enter your current password")
        String currentPassword,

        @NotBlank(message = "Enter a new password")
        @Size(min = 8, max = 72, message = "Password must be 8-72 characters")
        String newPassword) {
}
