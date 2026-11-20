package com.fitcheck.clothing;

import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * Gives older clothing items (uploaded before thumbnails existed) a thumbnail, once, in the
 * background after the app starts. Until an item has one, the API simply uses the full photo
 * as its thumbnail, so nothing breaks in the meantime.
 *
 * <p>Each item is handled in its own transaction, so one bad photo doesn't stop the rest.
 * Can be switched off with {@code fitcheck.thumbnails.backfill-on-startup=false}.
 */
@Component
public class ThumbnailBackfill {

    private static final Logger log = LoggerFactory.getLogger(ThumbnailBackfill.class);

    private final ClothingItemRepository items;
    private final ClothingService clothingService;
    private final boolean enabled;

    public ThumbnailBackfill(ClothingItemRepository items, ClothingService clothingService,
            @Value("${fitcheck.thumbnails.backfill-on-startup:true}") boolean enabled) {
        this.items = items;
        this.clothingService = clothingService;
        this.enabled = enabled;
    }

    @Async
    @EventListener(ApplicationReadyEvent.class)
    public void backfill() {
        if (!enabled) {
            return;
        }
        List<UUID> missing = items.findIdsWithoutThumbnail();
        if (missing.isEmpty()) {
            return;
        }
        log.info("Creating thumbnails for {} older clothing item(s)", missing.size());
        int done = 0;
        for (UUID id : missing) {
            try {
                clothingService.createMissingThumbnail(id);
                done++;
            } catch (RuntimeException e) {
                log.warn("Could not create a thumbnail for clothing item {}: {}", id, e.getMessage());
            }
        }
        log.info("Thumbnail backfill finished: {} of {} created", done, missing.size());
    }
}
