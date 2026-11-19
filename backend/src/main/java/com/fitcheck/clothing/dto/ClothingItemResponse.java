package com.fitcheck.clothing.dto;

import com.fitcheck.clothing.ClothingCategory;
import com.fitcheck.clothing.ClothingItem;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * A clothing item as the API returns it.
 *
 * <p>{@code imageUrl} is a fresh, short-lived signed link (SAS) to the photo. The client should
 * display it, not save it: it stops working after ~15 minutes, and the API hands out new ones
 * every time items are fetched. {@code blobName} is internal and deliberately not exposed.
 */
public record ClothingItemResponse(
        UUID id,
        String name,
        ClothingCategory category,
        String color,
        List<String> tags,
        String imageUrl,
        Instant createdAt) {

    public static ClothingItemResponse from(ClothingItem item, String imageUrl) {
        return new ClothingItemResponse(item.getId(), item.getName(), item.getCategory(), item.getColor(),
                item.getTags(), imageUrl, item.getCreatedAt());
    }
}
