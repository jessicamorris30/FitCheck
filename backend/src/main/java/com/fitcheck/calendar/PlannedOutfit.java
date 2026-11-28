package com.fitcheck.calendar;

import com.fitcheck.outfit.Outfit;
import com.fitcheck.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;

/**
 * One saved outfit put on one calendar day. Maps to the {@code planned_outfits} table (V6).
 *
 * <p>The day is a {@link LocalDate}: just year-month-day, no time zone, so it means the same
 * day to everyone (unlike an Instant, which would shift across midnight in other time zones).
 */
@Entity
@Table(name = "planned_outfits")
public class PlannedOutfit {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, updatable = false)
    private User owner;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "outfit_id", nullable = false, updatable = false)
    private Outfit outfit;

    @Column(nullable = false, updatable = false)
    private LocalDate day;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected PlannedOutfit() {
        // for JPA
    }

    public PlannedOutfit(User owner, Outfit outfit, LocalDate day) {
        this.owner = owner;
        this.outfit = outfit;
        this.day = day;
    }

    public UUID getId() {
        return id;
    }

    public Outfit getOutfit() {
        return outfit;
    }

    public LocalDate getDay() {
        return day;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
