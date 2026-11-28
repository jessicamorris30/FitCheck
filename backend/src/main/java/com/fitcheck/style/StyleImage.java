package com.fitcheck.style;

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

/** One photo of the user's style (V8, at most 5 per user). The files live in Blob Storage. */
@Entity
@Table(name = "style_images")
public class StyleImage {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, updatable = false)
    private User owner;

    /** The photo, downscaled to 1024 px JPEG (what Claude sees and the page shows large). */
    @Column(name = "blob_name", nullable = false, updatable = false, length = 300)
    private String blobName;

    /** A 320 px copy for the grid on the Account page. */
    @Column(name = "thumbnail_blob_name", nullable = false, updatable = false, length = 300)
    private String thumbnailBlobName;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected StyleImage() {
        // for JPA
    }

    public StyleImage(User owner, String blobName, String thumbnailBlobName) {
        this.owner = owner;
        this.blobName = blobName;
        this.thumbnailBlobName = thumbnailBlobName;
    }

    public UUID getId() {
        return id;
    }

    public String getBlobName() {
        return blobName;
    }

    public String getThumbnailBlobName() {
        return thumbnailBlobName;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
