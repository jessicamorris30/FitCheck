package com.fitcheck.style.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Body of {@code PUT /api/users/me/style}: {@code { "note": "...", "useWithAi": true }}.
 * Photos are added and removed separately.
 *
 * @param note      the user's own words about their style; blank = none
 * @param useWithAi whether the AI stylist should take the style into account
 */
public record StyleRequest(
        @Size(max = 1000, message = "Keep it under 1000 characters")
        String note,

        @NotNull(message = "useWithAi is required")
        Boolean useWithAi) {

    public StyleRequest {
        note = note == null || note.isBlank() ? null : note.strip();
    }
}
