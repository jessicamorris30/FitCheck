package com.fitcheck.style;

import com.fitcheck.storage.BlobStorageService;
import com.fitcheck.stylist.AiRefusedException;

import com.fitcheck.stylist.StylistClient;
import com.fitcheck.stylist.StylistModels.StyleSample;
import com.fitcheck.user.UserRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Writes Claude's PRIVATE notes on a user's style, from their style photos and note. The notes
 * are stored on {@link UserStyle} for the AI stylist and never shown to the user.
 *
 * <p>Runs in the background after a change is saved, so uploading a photo isn't slowed down by
 * Claude. It first waits a few seconds: adding 5 photos in a row then costs ONE Claude call
 * (the first four runs see a newer version and step aside for the last one).
 *
 * <p>Guard rails: only for confirmed emails (like the stylist itself), and at most
 * {@link #DAILY_LIMIT} Claude calls per user per day. Skipped notes just stay out of date; the
 * stylist can catch up later via {@link #describeIfStale}.
 */
@Component
public class StyleDescriber {

    private static final Logger log = LoggerFactory.getLogger(StyleDescriber.class);

    static final int DAILY_LIMIT = 10;

    /** What happened, for logs and tests. */
    public enum Outcome {
        /** Notes already match the current photos and note (or there's no style at all). */
        UP_TO_DATE,
        /** Claude wrote new notes. */
        WRITTEN,
        /** No photos and no note left: the notes were removed, no Claude call needed. */
        CLEARED,
        /** A newer change came in; that change's run will write the notes. */
        SUPERSEDED,
        /** The email isn't confirmed yet. */
        SKIPPED_UNCONFIRMED,
        /** Already {@link #DAILY_LIMIT} notes today. */
        SKIPPED_DAILY_LIMIT,
        /** Claude or storage failed; the notes stay out of date and are retried later. */
        FAILED
    }

    private final UserStyleRepository styles;
    private final StyleImageRepository photos;
    private final UserRepository users;
    private final BlobStorageService storage;
    private final StylistClient stylist;
    private final TransactionTemplate tx;
    private final Clock clock;
    private final Duration delay;

    public StyleDescriber(UserStyleRepository styles, StyleImageRepository photos, UserRepository users,
            BlobStorageService storage, StylistClient stylist, PlatformTransactionManager transactions, Clock clock,
            @Value("${fitcheck.style.describe-delay:5s}") Duration delay) {
        this.styles = styles;
        this.photos = photos;
        this.users = users;
        this.storage = storage;
        this.stylist = stylist;
        this.tx = new TransactionTemplate(transactions);
        this.clock = clock;
        this.delay = delay;
    }

    /** After a style change is saved: wait a moment (more changes may follow), then write the notes. */
    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onStyleChanged(StyleChanged event) {
        try {
            Thread.sleep(delay.toMillis());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return;
        }
        Outcome outcome = describe(event.userId(), event.version());
        log.info("Style notes for user {} (version {}): {}", event.userId(), event.version(), outcome);
    }

    /** Writes the notes now if they're out of date (used by the stylist to catch up, and by tests). */
    public Outcome describeIfStale(UUID userId) {
        return describe(userId, null);
    }

    /** Same, on a background thread: the stylist uses the older notes meanwhile. */
    @Async
    public void describeIfStaleInBackground(UUID userId) {
        Outcome outcome = describe(userId, null);
        log.info("Style notes catch-up for user {}: {}", userId, outcome);
    }

    /** A snapshot of what to describe, taken in a short read-only transaction. */
    private record Plan(int version, List<String> photoBlobs, String note) {
    }

    /**
     * @param expectedVersion the version this run is for; null = whatever is current
     */
    Outcome describe(UUID userId, Integer expectedVersion) {
        LocalDate today = LocalDate.now(clock.withZone(ZoneOffset.UTC));

        // 1. Decide, in a short transaction. Claude is NOT called while the database is in use.
        Object decision = tx.execute(status -> {
            UserStyle style = styles.findById(userId).orElse(null);
            if (style == null) {
                return Outcome.UP_TO_DATE;
            }
            if (expectedVersion != null && expectedVersion != style.getVersion()) {
                return Outcome.SUPERSEDED;
            }
            if (!style.isDescriptionStale()) {
                return Outcome.UP_TO_DATE;
            }
            List<String> blobs = photos.findAllByOwner_IdOrderByCreatedAtAscIdAsc(userId).stream()
                    .map(StyleImage::getBlobName).toList();
            if (blobs.isEmpty() && style.getNote() == null) {
                style.setDescription(null, style.getVersion()); // nothing left to describe
                return Outcome.CLEARED;
            }
            if (!users.findById(userId).map(u -> u.isEmailVerified()).orElse(false)) {
                return Outcome.SKIPPED_UNCONFIRMED;
            }
            if (style.descriptionsOn(today) >= DAILY_LIMIT) {
                return Outcome.SKIPPED_DAILY_LIMIT;
            }
            return new Plan(style.getVersion(), blobs, style.getNote());
        });
        if (decision instanceof Outcome outcome) {
            return outcome;
        }
        Plan plan = (Plan) decision;

        // 2. Ask Claude (no transaction open; this can take several seconds).
        String notes;
        boolean refused = false;
        try {
            List<byte[]> images = plan.photoBlobs().stream().map(storage::download).toList();
            notes = stylist.describeStyle(new StyleSample(images, plan.note()));
        } catch (AiRefusedException e) {
            // Declined (e.g. the photos aren't something it will describe): store "no notes" for
            // this version, so it isn't retried on every request.
            notes = null;
            refused = true;
        } catch (RuntimeException e) { // AiUnavailableException, or a photo couldn't be downloaded
            log.warn("Couldn't write style notes for user {}: {}", userId, e.getMessage());
            return Outcome.FAILED;
        }

        // 3. Save, only if nothing changed while Claude was thinking. The call counts either way.
        String result = notes;
        boolean declined = refused;
        return tx.execute(status -> {
            UserStyle style = styles.findForUpdateByUserId(userId).orElse(null);
            if (style == null) {
                return Outcome.SUPERSEDED; // account deleted meanwhile
            }
            style.countDescription(today);
            if (style.getVersion() != plan.version()) {
                return Outcome.SUPERSEDED;
            }
            style.setDescription(result, plan.version());
            if (declined) {
                log.info("Claude declined to describe the style of user {}", userId);
            }
            return Outcome.WRITTEN;
        });
    }
}
