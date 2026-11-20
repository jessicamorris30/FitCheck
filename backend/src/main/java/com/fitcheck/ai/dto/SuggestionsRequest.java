package com.fitcheck.ai.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Body of {@code POST /api/ai/outfit-suggestions}: what the user is going for. */
public record SuggestionsRequest(
        @NotBlank(message = "Describe the look you're going for")
        @Size(max = 500, message = "Please keep it under 500 characters")
        String prompt) {
}
