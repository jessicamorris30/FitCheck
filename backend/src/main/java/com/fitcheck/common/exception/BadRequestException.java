package com.fitcheck.common.exception;

/** The request can't be processed as sent (e.g. an invalid or expired link) → 400. */
public class BadRequestException extends RuntimeException {

    public BadRequestException(String message) {
        super(message);
    }
}
