package com.fitcheck.outfit;

import com.fitcheck.clothing.ClothingItem;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.util.UUID;

/**
 * One clothing item placed in one outfit, at a position. Maps to the {@code outfit_items}
 * "join table", which links outfits and clothing items many-to-many: an outfit has several
 * items, and one item (your favorite jeans) can appear in many outfits.
 *
 * <p>The table has no id column of its own: its primary key is the pair
 * (outfit_id, clothing_item_id). That's what {@link Key} represents, and it also means the same
 * item can't appear twice in one outfit.
 */
@Entity
@Table(name = "outfit_items")
public class OutfitItem {

    /** The composite primary key: which outfit + which clothing item. */
    @Embeddable
    public record Key(
            @Column(name = "outfit_id") UUID outfitId,
            @Column(name = "clothing_item_id") UUID clothingItemId) implements Serializable {
    }

    @EmbeddedId
    private Key id;

    /** @MapsId: fill Key.outfitId from this relationship instead of a separate column. */
    @MapsId("outfitId")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "outfit_id")
    private Outfit outfit;

    @MapsId("clothingItemId")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "clothing_item_id")
    private ClothingItem clothingItem;

    /** 0, 1, 2, ...: the order the items appear in the outfit. */
    @Column(nullable = false)
    private int position;

    protected OutfitItem() {
    }

    OutfitItem(Outfit outfit, ClothingItem clothingItem, int position) {
        this.outfit = outfit;
        this.clothingItem = clothingItem;
        this.position = position;
        this.id = new Key(outfit.getId(), clothingItem.getId());
    }

    public ClothingItem getClothingItem() {
        return clothingItem;
    }

    public int getPosition() {
        return position;
    }

    void setPosition(int position) {
        this.position = position;
    }
}
