package com.fitcheck.common.exception;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.util.Map;

/**
 * The one JSON shape every error response uses, so the frontend only has to handle one format.
 *
 * <pre>
 * { "status": 400, "error": "Bad Request", "message": "Validation failed",
 *   "timestamp": "...", "fieldErrors": { "email": "must be a well-formed email address" } }
 * </pre>
 *
 * @param fieldErrors per-field validation messages; left out of the JSON when there are none
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiError(int status, String error, String message, Instant timestamp, Map<String, String> fieldErrors) {

    public static ApiError of(int status, String error, String message) {
        return new ApiError(status, error, message, Instant.now(), null);
    }
}
