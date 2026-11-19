package com.fitcheck.auth;

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
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * One refresh token. Maps to the {@code refresh_tokens} table (V1__init_schema.sql).
 *
 * <p>We store only a SHA-256 hash of the token, never the token itself. If the database ever
 * leaked, the hashes couldn't be used to log in.
 *
 * <p>Every login starts a new "family". Each refresh replaces the current token with a new one
 * in the same family and marks the old one revoked. If a revoked token is ever presented again,
 * someone is replaying a stolen copy, so the whole family is revoked (see RefreshTokenService).
 */
@Entity
@Table(name = "refresh_tokens")
public class RefreshToken {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /** The account this token belongs to. LAZY: the user row is only loaded if we actually use it. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, updatable = false)
    private User user;

    @JdbcTypeCode(SqlTypes.CHAR) // the column is CHAR(64): a SHA-256 hash is always 64 hex characters
    @Column(name = "token_hash", nullable = false, unique = true, length = 64, updatable = false)
    private String tokenHash;

    @Column(name = "family_id", nullable = false, updatable = false)
    private UUID familyId;

    @Column(name = "expires_at", nullable = false, updatable = false)
    private Instant expiresAt;

    /** Set when the token is rotated, logged out, or its family is revoked. Null while still usable. */
    @Column(name = "revoked_at")
    private Instant revokedAt;

    /** The token that replaced this one at rotation (an audit trail of the family). */
    @Column(name = "replaced_by")
    private UUID replacedBy;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected RefreshToken() {
    }

    public RefreshToken(User user, String tokenHash, UUID familyId, Instant expiresAt) {
        this.user = user;
        this.tokenHash = tokenHash;
        this.familyId = familyId;
        this.expiresAt = expiresAt;
    }

    public boolean isRevoked() {
        return revokedAt != null;
    }

    public boolean isExpired(Instant now) {
        return !now.isBefore(expiresAt);
    }

    /** Marks this token used up and records which token took its place. */
    public void rotateTo(UUID replacementId, Instant now) {
        this.revokedAt = now;
        this.replacedBy = replacementId;
    }

    public UUID getId() {
        return id;
    }

    public User getUser() {
        return user;
    }

    public UUID getFamilyId() {
        return familyId;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public Instant getRevokedAt() {
        return revokedAt;
    }

    public UUID getReplacedBy() {
        return replacedBy;
    }
}
