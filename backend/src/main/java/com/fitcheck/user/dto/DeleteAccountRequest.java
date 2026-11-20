package com.fitcheck.user.dto;

import jakarta.validation.constraints.NotBlank;

/** Body of {@code DELETE /api/users/me}: the password, to confirm it's really you. */
public record DeleteAccountRequest(@NotBlank(message = "Enter your password to confirm") String password) {
}
