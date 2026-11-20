package com.fitcheck.ai;

import com.fitcheck.ai.dto.SuggestionsResponse;
import com.fitcheck.ai.dto.SuggestionsResponse.SuggestionDto;
import com.fitcheck.clothing.ClothingItem;
import com.fitcheck.clothing.ClothingItemRepository;
import com.fitcheck.clothing.dto.ClothingItemResponse;
import com.fitcheck.common.exception.BadRequestException;
import com.fitcheck.storage.BlobStorageService;
import com.fitcheck.storage.ImageType;
import com.fitcheck.storage.ThumbnailCache;
import com.fitcheck.storage.ThumbnailService;
import com.fitcheck.stylist.AiUnavailableException;
import com.fitcheck.stylist.StylistClient;
import com.fitcheck.stylist.StylistModels.Suggestion;
import com.fitcheck.stylist.StylistModels.SuggestionRequest;
import com.fitcheck.stylist.StylistModels.SuggestionResult;
import com.fitcheck.stylist.StylistModels.WardrobeEntry;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * The AI stylist: gathers the user's wardrobe, asks Claude for outfits, and checks the answer.
 *
 * <p>Deliberately NOT one @Transactional method: the Claude call can take 10-40 seconds, and
 * holding a database transaction (and connection) open that long would starve the rest of the
 * app. The repository calls before and after run in their own short transactions.
 */
@Service
public class OutfitSuggestionService {

    private static final Logger log = LoggerFactory.getLogger(OutfitSuggestionService.class);

    /** Upper bound on items sent to Claude, to keep cost and request size predictable. */
    static final int MAX_WARDROBE_ITEMS = 200;

    private final ClothingItemRepository clothing;
    private final BlobStorageService storage;
    private final ThumbnailCache thumbnails;
    private final ThumbnailService imageResizer;
    private final StylistClient stylist;
    private final AiUsageService usageService;

    public OutfitSuggestionService(ClothingItemRepository clothing, BlobStorageService storage,
            ThumbnailCache thumbnails, ThumbnailService imageResizer, StylistClient stylist,
            AiUsageService usageService) {
        this.clothing = clothing;
        this.storage = storage;
        this.thumbnails = thumbnails;
        this.imageResizer = imageResizer;
        this.stylist = stylist;
        this.usageService = usageService;
    }

    /** Largest side of an inspiration photo sent to Claude. Bigger costs more and adds nothing. */
    static final int INSPIRATION_MAX_SIDE = 1024;
    static final int MAX_PROMPT_LENGTH = 500;

    public SuggestionsResponse suggest(UUID ownerId, String prompt) {
        return suggest(ownerId, prompt, null);
    }

    /**
     * @param prompt      what the user is going for; may be blank if there's a photo
     * @param inspiration the uploaded inspiration photo's bytes; may be null if there's a prompt
     * @throws BadRequestException if both are missing, the prompt is too long, or the photo isn't a usable image
     */
    public SuggestionsResponse suggest(UUID ownerId, String prompt, byte[] inspiration) {
        boolean hasPrompt = prompt != null && !prompt.isBlank();
        if (!hasPrompt && inspiration == null) {
            throw new BadRequestException("Describe the look you're going for, add an inspiration photo, or both.");
        }
        if (hasPrompt && prompt.length() > MAX_PROMPT_LENGTH) {
            throw new BadRequestException("Please keep the description under 500 characters.");
        }
        // Checked BEFORE loading the wardrobe or calling Claude, so a bad photo costs nothing.
        byte[] inspirationJpeg = inspiration == null ? null : prepareInspiration(inspiration);

        // 1. The wardrobe, OLDEST first. A new item is added at the end, so the start of the list
        //    (and the prompt-cache prefix built from it) stays the same as the wardrobe grows.
        List<ClothingItem> items = clothing.findAllByOwner_IdOrderByCreatedAtDesc(ownerId).stream()
                .sorted(Comparator.comparing(ClothingItem::getCreatedAt).thenComparing(ClothingItem::getId))
                .limit(MAX_WARDROBE_ITEMS)
                .toList();
        if (items.isEmpty()) {
            throw new BadRequestException("Add some clothes to your wardrobe first, then ask the stylist.");
        }

        // 2. Guard rails: confirmed email + daily limit. This comes AFTER the free checks above,
        //    so a typo or a bad photo never uses up one of the user's daily requests.
        UUID usageId = usageService.checkAndRecord(ownerId);

        try {
            // 3. Ask Claude (the slow part).
            List<WardrobeEntry> wardrobe = items.stream().map(this::toEntry).toList();
            SuggestionResult result = stylist.suggest(
                    new SuggestionRequest(wardrobe, hasPrompt ? prompt.trim() : null, inspirationJpeg));

            // 4. Never trust the answer blindly: keep only ids that really are this user's items.
            //    (Claude only sees this user's items, but checking costs nothing and protects against
            //    any mistake, now or after future changes.)
            Map<UUID, ClothingItem> owned = items.stream()
                    .collect(Collectors.toMap(ClothingItem::getId, Function.identity()));

            List<SuggestionDto> suggestions = result.suggestions().stream()
                    .map(s -> toDto(s, owned))
                    .filter(Objects::nonNull)
                    .toList();
            if (suggestions.isEmpty()) {
                throw new AiUnavailableException("Claude's answer contained no usable outfits");
            }
            return new SuggestionsResponse(suggestions);
        } catch (AiUnavailableException e) {
            // Our side failed (outage, unusable answer): give the user their request back.
            usageService.refund(usageId);
            throw e;
        }
    }

    /**
     * Same checks as clothing uploads (magic bytes, then a full decode), then shrink to at most
     * 1024px as a JPEG. Re-encoding also drops the photo's metadata (EXIF), which can include the
     * GPS location where it was taken, so that never leaves the server. The photo is not stored.
     */
    private byte[] prepareInspiration(byte[] bytes) {
        if (ImageType.detect(bytes).isEmpty()) {
            throw new BadRequestException("The inspiration photo must be a JPEG, PNG or WebP image.");
        }
        try {
            return imageResizer.toJpeg(bytes, INSPIRATION_MAX_SIDE);
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("We couldn't read that inspiration photo. Please try a different one.");
        }
    }

    private WardrobeEntry toEntry(ClothingItem item) {
        byte[] thumb = null;
        if (item.getThumbnailBlobName() != null) {
            try {
                thumb = thumbnails.get(item.getThumbnailBlobName());
            } catch (RuntimeException e) {
                // A missing thumbnail shouldn't fail the whole request: Claude still gets the text.
                log.warn("Couldn't load thumbnail {} for the stylist: {}", item.getThumbnailBlobName(), e.getMessage());
            }
        }
        return new WardrobeEntry(item.getId(), item.getName(), item.getCategory().name(), item.getColor(),
                item.getTags(), thumb);
    }

    /** null = drop this suggestion (none of its items were valid). */
    private SuggestionDto toDto(Suggestion s, Map<UUID, ClothingItem> owned) {
        List<ClothingItemResponse> items = s.itemIds().stream()
                .distinct()
                .map(owned::get)
                .filter(Objects::nonNull)
                .map(item -> ClothingItemResponse.from(item, storage))
                .toList();
        if (items.isEmpty()) {
            return null;
        }
        String title = s.title() == null || s.title().isBlank() ? "Suggested outfit" : s.title().trim();
        return new SuggestionDto(title, s.reasoning() == null ? "" : s.reasoning().trim(), items);
    }
}
