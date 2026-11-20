package com.fitcheck.ai.dto;

import com.fitcheck.clothing.dto.ClothingItemResponse;
import java.util.List;

/**
 * AI outfit suggestions, ready to display: each suggestion carries its full clothing items
 * (same shape as /api/clothing, with signed image links), in the order Claude chose.
 */
public record SuggestionsResponse(List<SuggestionDto> suggestions) {

    public record SuggestionDto(String title, String reasoning, List<ClothingItemResponse> items) {
    }
}
