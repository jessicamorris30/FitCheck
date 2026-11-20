package com.fitcheck.outfit.dto;

import com.fitcheck.clothing.dto.ClothingItemResponse;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * An outfit as the API returns it. {@code items} are full clothing items (same shape as
 * /api/clothing returns, including fresh signed image links), in the outfit's order.
 *
 * @param aiPrompt what was asked of the AI stylist if the outfit came from a suggestion, else null
 */
public record OutfitResponse(
        UUID id,
        String name,
        String notes,
        String aiPrompt,
        List<ClothingItemResponse> items,
        Instant createdAt,
        Instant updatedAt) {
}
