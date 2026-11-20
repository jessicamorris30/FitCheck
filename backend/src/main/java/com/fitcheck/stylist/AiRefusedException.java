package com.fitcheck.stylist;

/**
 * Claude declined the request (stop reason "refusal"), even after the automatic fallback.
 * GlobalExceptionHandler maps it to 422 with a friendly message.
 */
public class AiRefusedException extends RuntimeException {

    public AiRefusedException(String message) {
        super(message);
    }
}
