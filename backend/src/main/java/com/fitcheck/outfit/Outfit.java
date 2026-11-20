package com.fitcheck.outfit;

import com.fitcheck.clothing.ClothingItem;
import com.fitcheck.user.User;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.springframework.data.domain.Persistable;

/**
 * A saved outfit: a name, optional notes, and an ordered list of the owner's clothing items.
 * Maps to the {@code outfits} table; the items live in {@code outfit_items} (see {@link OutfitItem}).
 */
@Entity
@Table(name = "outfits")
public class Outfit implements Persistable<UUID> {

    public static final int MAX_ITEMS = 20;

    /**
     * Assigned in the constructor (not generated on save), because each OutfitItem's key
     * includes the outfit id and is built before the first save.
     */
    @Id
    private UUID id;

    /**
     * Spring Data normally decides "new or existing?" by checking whether the id is null. Ours
     * never is, so we answer it ourselves via {@link Persistable#isNew()}: true until the outfit
     * is first saved or whenever it's loaded from the database.
     */
    @Transient
    private boolean isNew = true;

    @PostPersist
    @PostLoad
    void markNotNew() {
        this.isNew = false;
    }

    @Override
    public boolean isNew() {
        return isNew;
    }

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, updatable = false)
    private User owner;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(columnDefinition = "text")
    private String notes;

    /** The text prompt that produced this outfit, when it came from an AI suggestion (Phase 4). */
    @Column(name = "ai_prompt", columnDefinition = "text")
    private String aiPrompt;

    /**
     * The items, in order.
     * <ul>
     *   <li>{@code cascade = ALL}: saving or deleting the outfit saves or deletes its rows in outfit_items.
     *   <li>{@code orphanRemoval}: an item taken out of this list is deleted from outfit_items.
     * </ul>
     */
    @OneToMany(mappedBy = "outfit", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("position ASC")
    private List<OutfitItem> items = new ArrayList<>();

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Outfit() {
    }

    public Outfit(User owner, String name, String notes) {
        this.owner = owner;
        this.id = UUID.randomUUID(); // assigned up front: the outfit_items keys need it before saving
        rename(name, notes);
    }

    public void rename(String name, String notes) {
        this.name = name.trim();
        this.notes = notes == null || notes.isBlank() ? null : notes.trim();
    }

    public void setAiPrompt(String aiPrompt) {
        this.aiPrompt = aiPrompt;
    }

    /**
     * Makes the outfit contain exactly these clothing items, in this order (duplicates ignored).
     *
     * <p>Why not just {@code items.clear()} and re-add everything? Hibernate runs INSERTs before
     * DELETEs when it saves. Re-adding an item that was already in the outfit would INSERT a row
     * with the same (outfit, item) key before the old row is DELETEd, and the database rejects the
     * duplicate key. So we compare instead: keep the items that stay (just updating their position),
     * remove the ones that left, add the new ones.
     */
    public void replaceItems(List<ClothingItem> clothing) {
        // Remember the existing rows by clothing item id.
        Map<UUID, OutfitItem> existing = new LinkedHashMap<>();
        for (OutfitItem oi : items) {
            existing.put(oi.getClothingItem().getId(), oi);
        }

        List<OutfitItem> next = new ArrayList<>();
        for (ClothingItem c : clothing) {
            if (next.stream().anyMatch(oi -> oi.getClothingItem().getId().equals(c.getId()))) {
                continue; // duplicate in the request: keep the first one
            }
            OutfitItem row = existing.remove(c.getId());
            if (row == null) {
                row = new OutfitItem(this, c, next.size());
            } else {
                row.setPosition(next.size());
            }
            next.add(row);
        }

        // Rows left in "existing" were taken out of the outfit; orphanRemoval deletes them.
        items.removeAll(existing.values());
        // Put the list in the new order (same row objects, so no inserts for kept items).
        items.clear();
        items.addAll(next);
    }

    /** The clothing items in order. */
    public List<ClothingItem> getClothingItems() {
        return items.stream().map(OutfitItem::getClothingItem).toList();
    }

    @Override
    public UUID getId() {
        return id;
    }

    public User getOwner() {
        return owner;
    }

    public String getName() {
        return name;
    }

    public String getNotes() {
        return notes;
    }

    public String getAiPrompt() {
        return aiPrompt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
