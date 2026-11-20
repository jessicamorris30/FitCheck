package com.fitcheck.common.exception;

import com.fitcheck.stylist.AiRefusedException;
import com.fitcheck.stylist.AiUnavailableException;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * Turns exceptions thrown by controllers and services into {@link ApiError} JSON responses.
 *
 * <p>Without this, Spring returns its own default error format. Services can simply
 * {@code throw new NotFoundException(...)} and this class picks the right HTTP status.
 *
 * <p>Note: errors raised inside the security filters (e.g. "not logged in") happen before any
 * controller runs, so they're handled in SecurityConfig instead.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /** Our own "doesn't exist (or isn't yours)" exception → 404. */
    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<ApiError> handleNotFound(NotFoundException ex) {
        return respond(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    /** Request clashes with existing data (e.g. email already registered) → 409. */
    @ExceptionHandler(ConflictException.class)
    public ResponseEntity<ApiError> handleConflict(ConflictException ex) {
        return respond(HttpStatus.CONFLICT, ex.getMessage());
    }

    /** Invalid input that isn't a simple field validation error (e.g. expired link) → 400. */
    @ExceptionHandler(BadRequestException.class)
    public ResponseEntity<ApiError> handleBadRequest(BadRequestException ex) {
        return respond(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    /** Doing something too often → 429. */
    @ExceptionHandler(TooManyRequestsException.class)
    public ResponseEntity<ApiError> handleTooManyRequests(TooManyRequestsException ex) {
        return respond(HttpStatus.TOO_MANY_REQUESTS, ex.getMessage());
    }

    /** AI stylist not configured, or the Claude API is unreachable or failed → 503. Details go to the log. */
    @ExceptionHandler(AiUnavailableException.class)
    public ResponseEntity<ApiError> handleAiUnavailable(AiUnavailableException ex) {
        log.warn("AI unavailable: {}", ex.getMessage());
        return respond(HttpStatus.SERVICE_UNAVAILABLE, "The AI stylist is unavailable right now. Please try again later.");
    }

    /** Claude declined the request → 422, with a message the user can act on. */
    @ExceptionHandler(AiRefusedException.class)
    public ResponseEntity<ApiError> handleAiRefused(AiRefusedException ex) {
        return respond(HttpStatus.valueOf(422), "The stylist couldn't help with that request. Try describing it differently.");
    }

    /** Logged in, but not allowed (yet), e.g. AI before confirming the email → 403. */
    @ExceptionHandler(ForbiddenException.class)
    public ResponseEntity<ApiError> handleForbidden(ForbiddenException ex) {
        return respond(HttpStatus.FORBIDDEN, ex.getMessage());
    }

    /** Wrong credentials (e.g. bad login) → 401. */
    @ExceptionHandler(UnauthorizedException.class)
    public ResponseEntity<ApiError> handleUnauthorized(UnauthorizedException ex) {
        return respond(HttpStatus.UNAUTHORIZED, ex.getMessage());
    }

    /** One field is wrong in a way only the service can tell (e.g. wrong current password) → 400 + fieldErrors. */
    @ExceptionHandler(FieldErrorException.class)
    public ResponseEntity<ApiError> handleFieldError(FieldErrorException ex) {
        HttpStatus status = HttpStatus.BAD_REQUEST;
        return ResponseEntity.status(status).body(new ApiError(status.value(), status.getReasonPhrase(),
                ex.getMessage(), Instant.now(), Map.of(ex.getField(), ex.getMessage())));
    }

    /** Body is missing or isn't valid JSON → 400. */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiError> handleUnreadable(HttpMessageNotReadableException ex) {
        return respond(HttpStatus.BAD_REQUEST, "Request body is missing or malformed");
    }

    /**
     * A URL value has the wrong type, e.g. /api/clothing/not-a-uuid or ?category=CAPE → 400.
     * (Without this, it would fall through to the catch-all and wrongly become a 500.)
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiError> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        return respond(HttpStatus.BAD_REQUEST, "Invalid value for '" + ex.getName() + "'");
    }

    /** A required multipart part (e.g. "image") wasn't sent → 400. */
    @ExceptionHandler(MissingServletRequestPartException.class)
    public ResponseEntity<ApiError> handleMissingPart(MissingServletRequestPartException ex) {
        return respond(HttpStatus.BAD_REQUEST, "Missing required part: " + ex.getRequestPartName());
    }

    /** Upload bigger than spring.servlet.multipart.max-file-size (10 MB) → 413. */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiError> handleTooLarge(MaxUploadSizeExceededException ex) {
        return respond(HttpStatus.CONTENT_TOO_LARGE, "That file is too large (max 10 MB)");
    }

    /** A URL that no controller handles → 404. */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiError> handleNoResource(NoResourceFoundException ex) {
        return respond(HttpStatus.NOT_FOUND, "No endpoint " + ex.getResourcePath());
    }

    /** A request body failed its @Valid checks (e.g. @Email, @NotBlank) → 400 with one message per field. */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException ex) {
        Map<String, String> fieldErrors = new LinkedHashMap<>();
        for (FieldError fe : ex.getBindingResult().getFieldErrors()) {
            fieldErrors.putIfAbsent(fe.getField(), fe.getDefaultMessage());
        }
        HttpStatus status = HttpStatus.BAD_REQUEST;
        return ResponseEntity.status(status)
                .body(new ApiError(status.value(), status.getReasonPhrase(), "Validation failed", Instant.now(), fieldErrors));
    }

    /** Anything unexpected → 500. The details go to the log, never to the client. */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleUnexpected(Exception ex) {
        log.error("Unhandled exception", ex);
        return respond(HttpStatus.INTERNAL_SERVER_ERROR, "Something went wrong");
    }

    private static ResponseEntity<ApiError> respond(HttpStatus status, String message) {
        return ResponseEntity.status(status).body(ApiError.of(status.value(), status.getReasonPhrase(), message));
    }
}
