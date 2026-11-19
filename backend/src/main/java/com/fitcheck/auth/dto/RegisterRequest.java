package com.fitcheck.auth.dto;

/**
 * Body of POST /api/auth/register.
 * TODO: add Bean Validation (@Email, @NotBlank, password length).
 */
public record RegisterRequest(String email, String password) {
}
