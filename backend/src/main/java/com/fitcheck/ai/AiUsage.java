package com.fitcheck.ai;

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
import java.util.UUID;

/** One AI stylist request by one user, at one moment. Maps to {@code ai_usage} (V4__ai_usage.sql). */
@Entity
@Table(name = "ai_usage")
public class AiUsage {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, updatable = false)
    private User user;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected AiUsage() {
    }

    public AiUsage(User user, Instant createdAt) {
        this.user = user;
        this.createdAt = createdAt;
    }

    public UUID getId() {
        return id;
    }
}
