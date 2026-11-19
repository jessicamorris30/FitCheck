package com.fitcheck.outfit.dto;

import java.util.List;
import java.util.UUID;

/**
 * Create/update an outfit.
 * TODO: add Bean Validation.
 */
public record OutfitRequest(String name, String notes, List<UUID> itemIds) {
}
