package com.fitcheck.auth.dto;

import com.fitcheck.user.ProfileRules;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * JSON body of {@code POST /api/auth/register}:
 * {@code { "email", "password", "username", "firstName", "lastName" }}.
 *
 * <p>The annotations are Bean Validation rules. When the controller marks this parameter
 * with {@code @Valid}, Spring checks them before our code runs and returns a 400 listing
 * every broken rule (see GlobalExceptionHandler).
 */
public record RegisterRequest(
        @NotBlank(message = "Email is required")
        @Email(message = "Must be a valid email address")
        @Size(max = 320, message = "Email is too long")
        String email,

        // BCrypt only uses the first 72 bytes of a password, so we cap it there.
        @NotBlank(message = "Password is required")
        @Size(min = 8, max = 72, message = "Password must be 8-72 characters")
        String password,

        @NotBlank(message = "Username is required")
        @Pattern(regexp = ProfileRules.USERNAME_REGEX, message = ProfileRules.USERNAME_MESSAGE)
        String username,

        @NotBlank(message = "First name is required")
        @Size(max = ProfileRules.NAME_MAX, message = "First name is too long (max 50 characters)")
        @Pattern(regexp = ProfileRules.NAME_REGEX, message = "First name contains characters that aren't allowed")
        String firstName,

        @NotBlank(message = "Last name is required")
        @Size(max = ProfileRules.NAME_MAX, message = "Last name is too long (max 50 characters)")
        @Pattern(regexp = ProfileRules.NAME_REGEX, message = "Last name contains characters that aren't allowed")
        String lastName) {

    /**
     * Runs when Jackson builds this object from JSON, which happens before validation.
     * Trimming here means " ada@example.com " passes @Email, and " Ada.L " becomes the username
     * "ada.l" before its rules are checked. Passwords are left exactly as typed.
     */
    public RegisterRequest {
        if (email != null) {
            email = email.trim();
        }
        username = ProfileRules.normalizeUsername(username);
        firstName = ProfileRules.cleanName(firstName);
        lastName = ProfileRules.cleanName(lastName);
    }
}
