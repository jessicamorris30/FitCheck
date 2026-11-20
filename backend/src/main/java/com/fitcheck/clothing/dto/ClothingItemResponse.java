package com.fitcheck.clothing.dto;

import com.fitcheck.clothing.ClothingCategory;
import com.fitcheck.clothing.ClothingItem;
import com.fitcheck.storage.BlobStorageService;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * A clothing item as the API returns it.
 *
 * <p>{@code imageUrl} (full photo) and {@code thumbnailUrl} (small JPEG, for grids) are fresh,
 * short-lived signed links (SAS). The client should display them, not save them: they stop
 * working after ~15 minutes, and the API hands out new ones every time items are fetched.
 * While an older item's thumbnail hasn't been made yet, thumbnailUrl is the full photo.
 * Blob names are internal and deliberately not exposed.
 */
public record ClothingItemResponse(
        UUID id,
        String name,
        ClothingCategory category,
        String color,
        List<String> tags,
        String imageUrl,
        String thumbnailUrl,
        Instant createdAt) {

    /** The one place that turns an item into a response, signing both image links. */
    public static ClothingItemResponse from(ClothingItem item, BlobStorageService storage) {
        String imageUrl = storage.readUrl(item.getBlobName());
        String thumbnailUrl = item.getThumbnailBlobName() != null ? storage.readUrl(item.getThumbnailBlobName()) : imageUrl;
        return new ClothingItemResponse(item.getId(), item.getName(), item.getCategory(), item.getColor(),
                item.getTags(), imageUrl, thumbnailUrl, item.getCreatedAt());
    }
}
