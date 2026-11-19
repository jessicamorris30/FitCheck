package com.fitcheck.clothing.dto;

import com.fitcheck.clothing.ClothingCategory;
import com.fitcheck.clothing.ClothingItem;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * The details of a clothing item, sent as the {@code metadata} part of an upload
 * (and later as the body of an edit).
 *
 * <p>{@code List<@Size(...) String>} validates each tag individually, not just the list.
 */
public record ClothingItemRequest(
        @NotBlank(message = "Name is required")
        @Size(max = 120, message = "Name is too long (max 120 characters)")
        String name,

        @NotNull(message = "Category is required")
        ClothingCategory category,

        @Size(max = 40, message = "Color is too long (max 40 characters)")
        String color,

        @Size(max = ClothingItem.MAX_TAGS, message = "Too many tags (max 20)")
        List<@Size(max = ClothingItem.MAX_TAG_LENGTH, message = "Each tag must be at most 30 characters") String> tags) {
}
