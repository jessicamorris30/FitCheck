package com.fitcheck.common.exception;

/** The caller is doing something too often (e.g. requesting confirmation emails) → 429. */
public class TooManyRequestsException extends RuntimeException {

    public TooManyRequestsException(String message) {
        super(message);
    }
}
