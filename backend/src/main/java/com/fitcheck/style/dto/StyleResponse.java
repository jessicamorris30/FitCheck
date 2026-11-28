package com.fitcheck.style.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * "My style" as the browser sees it: the user's note, the switch and their photos.
 *
 * <p>Claude's private notes on the style are deliberately NOT here. They're for the AI stylist
 * only and never leave the server (the owner asked for that).
 *
 * @param useWithAi whether the AI stylist takes this style into account
 * @param maxPhotos how many photos may be added in total (5)
 * @param updatedAt null if the user has never set anything
 */
public record StyleResponse(String note, boolean useWithAi, List<Photo> photos, int maxPhotos, Instant updatedAt) {

    /** One style photo, with short-lived signed links (like clothing photos). */
    public record Photo(UUID id, String imageUrl, String thumbnailUrl, Instant addedAt) {
    }
}
