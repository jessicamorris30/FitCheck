package com.fitcheck.admin;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;

/**
 * One admin action (V10 admin_audit_log): who did what, to whom, when. Emails are copied in, so
 * the record still makes sense after either account is deleted.
 */
@Entity
@Table(name = "admin_audit_log")
public class AdminAuditEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "admin_id")
    private UUID adminId;

    @Column(name = "admin_email", nullable = false, length = 320)
    private String adminEmail;

    @Column(nullable = false, length = 40)
    private String action;

    @Column(name = "target_user_id")
    private UUID targetUserId;

    @Column(name = "target_email", length = 320)
    private String targetEmail;

    @Column(columnDefinition = "text")
    private String details;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected AdminAuditEntry() {
        // for JPA
    }

    public AdminAuditEntry(UUID adminId, String adminEmail, String action, UUID targetUserId, String targetEmail,
            String details) {
        this.adminId = adminId;
        this.adminEmail = adminEmail;
        this.action = action;
        this.targetUserId = targetUserId;
        this.targetEmail = targetEmail;
        this.details = details;
    }

    public UUID getId() {
        return id;
    }

    public UUID getAdminId() {
        return adminId;
    }

    public String getAdminEmail() {
        return adminEmail;
    }

    public String getAction() {
        return action;
    }

    public UUID getTargetUserId() {
        return targetUserId;
    }

    public String getTargetEmail() {
        return targetEmail;
    }

    public String getDetails() {
        return details;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
