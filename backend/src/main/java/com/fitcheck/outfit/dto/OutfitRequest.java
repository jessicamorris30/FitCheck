package com.fitcheck.outfit.dto;

import com.fitcheck.outfit.Outfit;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

/**
 * Body of {@code POST /api/outfits} (and later {@code PUT}): a name, optional notes, and the
 * clothing item ids in the order they should appear.
 */
public record OutfitRequest(
        @NotBlank(message = "Name is required")
        @Size(max = 120, message = "Name is too long (max 120 characters)")
        String name,

        @Size(max = 1000, message = "Notes are too long (max 1000 characters)")
        String notes,

        @NotEmpty(message = "Add at least one item")
        @Size(max = Outfit.MAX_ITEMS, message = "An outfit can have at most 20 items")
        List<@NotNull(message = "Item id is missing") UUID> itemIds) {
}
