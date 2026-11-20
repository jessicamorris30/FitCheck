package com.fitcheck.auth;

/**
 * Event: "a password reset email should go out". Published by PasswordResetService, sent after
 * the transaction commits by PasswordResetEmailListener (same pattern as email confirmation).
 */
public record PasswordResetRequested(String email, String resetUrl) {
}
