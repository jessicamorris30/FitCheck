package com.fitcheck.ai.dto;

import java.util.List;
import java.util.UUID;

/**
 * Text part of POST /api/ai/outfit-suggestions (the optional inspiration image arrives as a separate multipart file).
 */
public record OutfitSuggestionRequest(String prompt, List<UUID> itemIds) {
}
