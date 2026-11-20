package com.fitcheck.stylist;

/**
 * The AI stylist can't answer right now: no API key configured, the API is down or overloaded,
 * or the answer was unusable. GlobalExceptionHandler maps it to 503 Service Unavailable.
 */
public class AiUnavailableException extends RuntimeException {

    public AiUnavailableException(String message) {
        super(message);
    }

    public AiUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
