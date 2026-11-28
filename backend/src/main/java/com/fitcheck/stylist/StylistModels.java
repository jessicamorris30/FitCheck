package com.fitcheck.stylist;

import java.util.List;
import java.util.UUID;

/**
 * The data that goes into and comes out of the AI stylist, in FitCheck's own terms.
 * Nothing here depends on the Anthropic SDK, so services and tests can use these freely.
 */
public final class StylistModels {

    private StylistModels() {
    }

    /**
     * One piece of clothing as shown to Claude.
     *
     * @param thumbnailJpeg the small photo, or null if it doesn't have one yet (then Claude only reads the details)
     */
    public record WardrobeEntry(UUID id, String name, String category, String color, List<String> tags,
            byte[] thumbnailJpeg) {
    }

    /**
     * Everything Claude needs for one request. At least one of prompt / inspirationJpeg is set.
     *
     * @param wardrobe        the user's clothes, in a stable order (oldest first; see OutfitSuggestionService)
     * @param prompt          what the user is going for, e.g. "rainy-day office look"; may be null
     * @param inspirationJpeg a photo of a look the user likes (already downscaled to JPEG); may be null
     * @param styleNotes      the private notes on the user's personal style (Phase 7); null if they
     *                        have none or switched the feature off
     */
    public record SuggestionRequest(List<WardrobeEntry> wardrobe, String prompt, byte[] inspirationJpeg,
            String styleNotes) {

        public SuggestionRequest(List<WardrobeEntry> wardrobe, String prompt) {
            this(wardrobe, prompt, null, null);
        }

        public SuggestionRequest(List<WardrobeEntry> wardrobe, String prompt, byte[] inspirationJpeg) {
            this(wardrobe, prompt, inspirationJpeg, null);
        }
    }

    /**
     * A user's examples of their own style (Phase 7): up to 5 photos and/or a note in their words.
     *
     * @param photosJpeg already downscaled JPEGs (no metadata); may be empty
     * @param note       the user's own description; may be null
     */
    public record StyleSample(List<byte[]> photosJpeg, String note) {
    }

    /** One suggested outfit, pointing at wardrobe entries by id. */
    public record Suggestion(String title, List<UUID> itemIds, String reasoning) {
    }

    public record SuggestionResult(List<Suggestion> suggestions) {
    }
}
