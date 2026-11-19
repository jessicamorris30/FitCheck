package com.fitcheck.ai.dto;

import java.util.List;
import java.util.UUID;

/**
 * AI outfit suggestions. Also the shape of the structured output requested from Claude.
 */
public record OutfitSuggestionResponse(List<OutfitSuggestionResponse.Suggestion> suggestions) {

    public record Suggestion(String title, List<UUID> itemIds, String reasoning) {
    }
}
