package com.fitcheck.user.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

/**
 * Body of {@code PUT /api/users/me/preferences}: {@code { "theme": "system" | "light" | "dark" }}.
 *
 * @param theme "system" follows the device's own light/dark setting
 */
public record PreferencesRequest(
        @NotNull(message = "Theme is required")
        @Pattern(regexp = "system|light|dark", message = "Theme must be system, light or dark")
        String theme) {
}
