package com.fitcheck.user;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

/**
 * A FitCheck account. Maps to the {@code users} table created in V1__init_schema.sql.
 *
 * <p>The password is never stored, only its BCrypt hash. Hashing happens in AuthService (step 3).
 */
@Entity
@Table(name = "users")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 320)
    private String email;

    @Column(name = "password_hash", nullable = false, length = 100)
    private String passwordHash;

    /** Lower case, unique; can be used to log in. Null for accounts made before Phase 7 until set. */
    @Column(length = 30)
    private String username;

    @Column(name = "first_name", length = 50)
    private String firstName;

    @Column(name = "last_name", length = 50)
    private String lastName;

    /** "system", "light" or "dark"; null = never chosen (treated like "system"). */
    @Column(length = 10)
    private String theme;

    /** When an admin suspended the account (V10); null = active. */
    @Column(name = "frozen_at")
    private Instant frozenAt;

    @Column(name = "frozen_reason", columnDefinition = "text")
    private String frozenReason;

    /** Last successful password login. */
    @Column(name = "last_login_at")
    private Instant lastLoginAt;

    /** Last time the app was used (a login or a session refresh, every ~15 minutes while active). */
    @Column(name = "last_active_at")
    private Instant lastActiveAt;

    /** When the user clicked the link in their confirmation email; null until then. */
    @Column(name = "email_verified_at")
    private Instant emailVerifiedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /** Required by JPA; Hibernate uses it to build objects from database rows. */
    protected User() {
    }

    public User(String email, String passwordHash) {
        this.email = email;
        this.passwordHash = passwordHash;
    }

    public UUID getId() {
        return id;
    }

    public String getEmail() {
        return email;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public String getUsername() {
        return username;
    }

    public String getFirstName() {
        return firstName;
    }

    public String getLastName() {
        return lastName;
    }

    /** Sets username and name together (sign-up, "finish your profile", profile editing). */
    public void setProfile(String username, String firstName, String lastName) {
        this.username = username;
        this.firstName = firstName;
        this.lastName = lastName;
    }

    public boolean isFrozen() {
        return frozenAt != null;
    }

    public Instant getFrozenAt() {
        return frozenAt;
    }

    public String getFrozenReason() {
        return frozenReason;
    }

    /** Suspends the account (admin). */
    public void freeze(String reason, Instant now) {
        this.frozenAt = now;
        this.frozenReason = reason;
    }

    public void unfreeze() {
        this.frozenAt = null;
        this.frozenReason = null;
    }

    public Instant getLastLoginAt() {
        return lastLoginAt;
    }

    public Instant getLastActiveAt() {
        return lastActiveAt;
    }

    /** A successful password login (also counts as activity). */
    public void recordLogin(Instant now) {
        this.lastLoginAt = now;
        this.lastActiveAt = now;
    }

    /** The app was used (session refreshed). */
    public void recordActivity(Instant now) {
        this.lastActiveAt = now;
    }

    public String getTheme() {
        return theme;
    }

    public void setTheme(String theme) {
        this.theme = theme;
    }

    /** False for accounts made before usernames existed, until they fill them in. */
    public boolean isProfileComplete() {
        return username != null && firstName != null && lastName != null;
    }

    public boolean isEmailVerified() {
        return emailVerifiedAt != null;
    }

    public Instant getEmailVerifiedAt() {
        return emailVerifiedAt;
    }

    /** Records the first successful verification; clicking the link again changes nothing. */
    public void markEmailVerified(Instant now) {
        if (emailVerifiedAt == null) {
            emailVerifiedAt = now;
        }
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
