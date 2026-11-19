package com.fitcheck.common.exception;

import java.time.Instant;

/**
 * Standard error response body.
 */
public record ApiError(int status, String error, String message, Instant timestamp) {
}
