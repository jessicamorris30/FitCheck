package com.fitcheck.style;

import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Gives the AI stylist the private notes on a user's personal style (step 7.5). Only the notes
 * (text) are sent with stylist requests, never the style photos: that keeps every request cheap
 * and fast, and the photos are only looked at once per change (see StyleDescriber).
 */
@Component
public class StyleNotesLookup {

    private final UserStyleRepository styles;
    private final StyleDescriber describer;
    private final TransactionTemplate readOnly;

    public StyleNotesLookup(UserStyleRepository styles, StyleDescriber describer, PlatformTransactionManager transactions) {
        this.styles = styles;
        this.describer = describer;
        this.readOnly = new TransactionTemplate(transactions);
        this.readOnly.setReadOnly(true);
    }

    /** What's stored right now: the switch, the notes and whether they're out of date. */
    private record Snapshot(boolean useWithAi, String notes, boolean stale) {
    }

    /**
     * The notes for this user's stylist request, or null (no style, switched off, nothing usable).
     *
     * <ul>
     *   <li>Up to date: used as they are.</li>
     *   <li>Out of date, but older notes exist (e.g. Claude was briefly down, or today's cap was
     *       reached): the older notes are used now, and new ones are written in the background.</li>
     *   <li>No notes at all yet: they're written first. This is the only case that makes a stylist
     *       request a few seconds slower, once.</li>
     * </ul>
     */
    public String notesFor(UUID userId) {
        Snapshot now = snapshot(userId);
        if (now == null || !now.useWithAi()) {
            return null;
        }
        if (!now.stale()) {
            return now.notes();
        }
        if (now.notes() != null) {
            describer.describeIfStaleInBackground(userId);
            return now.notes();
        }
        describer.describeIfStale(userId);
        Snapshot after = snapshot(userId);
        return after == null ? null : after.notes();
    }

    private Snapshot snapshot(UUID userId) {
        return readOnly.execute(status -> styles.findById(userId)
                .map(s -> new Snapshot(s.isUseWithAi(), s.getAiDescription(), s.isDescriptionStale()))
                .orElse(null));
    }
}
