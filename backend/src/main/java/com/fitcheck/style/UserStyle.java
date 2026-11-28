package com.fitcheck.style;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.hibernate.annotations.UpdateTimestamp;

/**
 * A user's "My style" settings (V8): their note, the on/off switch, and Claude's PRIVATE notes on
 * the style. One row per user, created the first time they touch the feature.
 *
 * <p>{@link #aiDescription} must never leave the server: it is not part of any response DTO.
 */
@Entity
@Table(name = "user_styles")
public class UserStyle {

    /** Same as the user's id: one style per user. */
    @Id
    @Column(name = "user_id")
    private UUID userId;

    @Column(columnDefinition = "text")
    private String note;

    @Column(name = "use_with_ai", nullable = false)
    private boolean useWithAi = true;

    @Column(name = "ai_description", columnDefinition = "text")
    private String aiDescription;

    @Column(nullable = false)
    private int version;

    @Column(name = "described_version")
    private Integer describedVersion;

    @Column(name = "descriptions_day")
    private LocalDate descriptionsDay;

    @Column(name = "descriptions_count", nullable = false)
    private int descriptionsCount;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected UserStyle() {
        // for JPA
    }

    public UserStyle(UUID userId) {
        this.userId = userId;
    }

    public UUID getUserId() {
        return userId;
    }

    public String getNote() {
        return note;
    }

    public boolean isUseWithAi() {
        return useWithAi;
    }

    public void setUseWithAi(boolean useWithAi) {
        this.useWithAi = useWithAi;
    }

    public String getAiDescription() {
        return aiDescription;
    }

    public int getVersion() {
        return version;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    /** Changes the note; returns true if it actually changed (so the notes need rewriting). */
    public boolean changeNote(String newNote) {
        if (java.util.Objects.equals(note, newNote)) {
            return false;
        }
        note = newNote;
        return true;
    }

    /** Something the description is based on changed (photo added/removed, note edited). */
    public void markChanged() {
        version++;
    }

    /** True if Claude's notes were written for an older version of the photos/note. */
    public boolean isDescriptionStale() {
        return describedVersion == null || describedVersion != version;
    }

    /** Stores Claude's notes (or null: nothing to describe) as matching {@code forVersion}. */
    public void setDescription(String description, int forVersion) {
        this.aiDescription = description;
        this.describedVersion = forVersion;
    }

    /** How many notes were written on {@code today} so far. */
    public int descriptionsOn(LocalDate today) {
        return today.equals(descriptionsDay) ? descriptionsCount : 0;
    }

    /** Counts one Claude call towards {@code today}'s cap. */
    public void countDescription(LocalDate today) {
        descriptionsCount = descriptionsOn(today) + 1;
        descriptionsDay = today;
    }
}
