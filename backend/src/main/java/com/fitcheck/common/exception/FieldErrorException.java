package com.fitcheck.common.exception;

/**
 * A problem with one specific input field that only the service can detect (e.g. "current
 * password is incorrect"), as opposed to format rules that Bean Validation checks.
 * GlobalExceptionHandler maps it to 400 with {@code fieldErrors: {field: message}}, so the form
 * can show the message right under that field.
 */
public class FieldErrorException extends RuntimeException {

    private final String field;

    public FieldErrorException(String field, String message) {
        super(message);
        this.field = field;
    }

    public String getField() {
        return field;
    }
}
