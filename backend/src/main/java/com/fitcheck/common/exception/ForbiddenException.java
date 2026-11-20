package com.fitcheck.common.exception;

/**
 * The user is logged in but not allowed to do this yet (e.g. use the AI stylist before
 * confirming their email). GlobalExceptionHandler maps it to 403 Forbidden.
 */
public class ForbiddenException extends RuntimeException {

    public ForbiddenException(String message) {
        super(message);
    }
}
