package com.fitcheck.user.dto;

import com.fitcheck.user.ProfileRules;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * JSON body of {@code PUT /api/users/me/profile}: {@code { "username", "firstName", "lastName" }}.
 * Used by "finish your profile" (accounts from before Phase 7) and by editing on the Account page.
 * Same rules as sign-up (see {@link ProfileRules}).
 */
public record ProfileRequest(
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

    public ProfileRequest {
        username = ProfileRules.normalizeUsername(username);
        firstName = ProfileRules.cleanName(firstName);
        lastName = ProfileRules.cleanName(lastName);
    }
}
