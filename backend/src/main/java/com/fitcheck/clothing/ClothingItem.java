package com.fitcheck.clothing;

import com.fitcheck.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

/**
 * One piece of clothing in a user's wardrobe. Maps to the {@code clothing_items} table.
 *
 * <p>This row holds the facts ("Blue jeans, BOTTOM, tagged casual/denim"); the photo itself lives
 * in Blob Storage under {@link #blobName}. See BlobStorageService.
 */
@Entity
@Table(name = "clothing_items")
public class ClothingItem {

    public static final int MAX_TAGS = 20;
    public static final int MAX_TAG_LENGTH = 30;

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /** The owner. Every query for clothing items filters by this, so users only see their own. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, updatable = false)
    private User owner;

    @Column(nullable = false, length = 120)
    private String name;

    /** Stored as the enum's name ("TOP", "SHOES", ...). Never ORDINAL: reordering the enum would corrupt data. */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ClothingCategory category;

    @Column(length = 40)
    private String color;

    /**
     * Free-form labels like "casual", "summer", "work". Stored in a single Postgres TEXT[]
     * (array) column instead of a separate table: simple, and enough while tags are just labels.
     */
    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(nullable = false, columnDefinition = "text[]")
    private List<String> tags = new ArrayList<>();

    /** Where the photo lives in Blob Storage, e.g. {@code users/<ownerId>/<uuid>.jpg}. Never changes. */
    @Column(name = "blob_name", nullable = false, unique = true, updatable = false)
    private String blobName;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected ClothingItem() {
    }

    public ClothingItem(User owner, String name, ClothingCategory category, String color, List<String> tags,
            String blobName) {
        this.owner = owner;
        this.blobName = blobName;
        updateDetails(name, category, color, tags);
    }

    /** Changes everything a user is allowed to edit. The photo and owner stay fixed. */
    public void updateDetails(String name, ClothingCategory category, String color, List<String> tags) {
        this.name = name.trim();
        this.category = category;
        this.color = color == null || color.isBlank() ? null : color.trim();
        this.tags = normalizeTags(tags);
    }

    /**
     * " Summer ", "summer", "", "WORK" → ["summer", "work"]: trimmed, lowercase, no blanks, no
     * duplicates, original order kept. Consistent tags make filtering and AI matching reliable.
     */
    static List<String> normalizeTags(List<String> raw) {
        if (raw == null) {
            return new ArrayList<>();
        }
        Set<String> clean = new LinkedHashSet<>();
        for (String tag : raw) {
            if (tag != null && !tag.isBlank()) {
                clean.add(tag.trim().toLowerCase(Locale.ROOT));
            }
        }
        return new ArrayList<>(clean);
    }

    public UUID getId() {
        return id;
    }

    public User getOwner() {
        return owner;
    }

    public String getName() {
        return name;
    }

    public ClothingCategory getCategory() {
        return category;
    }

    public String getColor() {
        return color;
    }

    public List<String> getTags() {
        return List.copyOf(tags); // callers get a read-only copy; changes go through updateDetails
    }

    public String getBlobName() {
        return blobName;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
