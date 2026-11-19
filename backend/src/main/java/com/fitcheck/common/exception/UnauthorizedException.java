package com.fitcheck.common.exception;

/**
 * Thrown when credentials are wrong (e.g. bad email/password at login).
 * GlobalExceptionHandler maps it to 401 Unauthorized.
 */
public class UnauthorizedException extends RuntimeException {

    public UnauthorizedException(String message) {
        super(message);
    }
}
