package com.fitcheck.auth.dto;

/**
 * Body of POST /api/auth/login.
 * TODO: add Bean Validation.
 */
public record LoginRequest(String email, String password) {
}
