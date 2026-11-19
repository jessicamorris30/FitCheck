package com.fitcheck.common.exception;

/**
 * Thrown when a resource does not exist or does not belong to the current user (mapped to 404).
 */
public class NotFoundException extends RuntimeException {

    public NotFoundException(String message) {
        super(message);
    }
}
