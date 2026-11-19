package com.fitcheck.common.exception;

/**
 * Thrown when a request clashes with existing data, e.g. registering an email that is
 * already taken. GlobalExceptionHandler maps it to 409 Conflict.
 */
public class ConflictException extends RuntimeException {

    public ConflictException(String message) {
        super(message);
    }
}
