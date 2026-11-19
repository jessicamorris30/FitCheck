package com.fitcheck.outfit.dto;

import com.fitcheck.clothing.dto.ClothingItemResponse;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Outfit with its clothing items.
 */
public record OutfitResponse(UUID id, String name, String notes, List<ClothingItemResponse> items, Instant createdAt) {
}
