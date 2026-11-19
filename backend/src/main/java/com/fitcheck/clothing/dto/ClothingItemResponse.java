package com.fitcheck.clothing.dto;

import com.fitcheck.clothing.ClothingCategory;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Clothing item as returned to the client; imageUrl is a short-lived SAS URL.
 */
public record ClothingItemResponse(UUID id, String name, ClothingCategory category, String color, List<String> tags, String imageUrl, Instant createdAt) {
}
