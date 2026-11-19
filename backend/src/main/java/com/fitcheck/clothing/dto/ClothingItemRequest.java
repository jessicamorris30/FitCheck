package com.fitcheck.clothing.dto;

import com.fitcheck.clothing.ClothingCategory;
import java.util.List;

/**
 * Metadata sent with an upload (multipart part) or an update.
 * TODO: add Bean Validation.
 */
public record ClothingItemRequest(String name, ClothingCategory category, String color, List<String> tags) {
}
