package com.fitcheck.clothing;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Database access for {@link ClothingItem}.
 *
 * <p><b>Rule:</b> every method takes the owner's id. There's deliberately no "find any item by id"
 * used by the API: {@code findByIdAndOwner_Id} returns empty for someone else's item, so the API
 * answers 404 and doesn't even reveal that the item exists.
 *
 * <p>{@code Owner_Id} walks the relationship (item → owner → id), so Spring generates
 * {@code WHERE user_id = ?}.
 */
public interface ClothingItemRepository extends JpaRepository<ClothingItem, UUID> {

    /** The whole wardrobe, newest first. */
    List<ClothingItem> findAllByOwner_IdOrderByCreatedAtDesc(UUID ownerId);

    /** One category of the wardrobe (e.g. only SHOES), newest first. */
    List<ClothingItem> findAllByOwner_IdAndCategoryOrderByCreatedAtDesc(UUID ownerId, ClothingCategory category);

    /** One item, but only if it belongs to this owner. */
    Optional<ClothingItem> findByIdAndOwner_Id(UUID id, UUID ownerId);

    long countByOwner_Id(UUID ownerId);

    /**
     * Several items at once (WHERE id IN (...) AND user_id = ?), used when building outfits.
     * Ids that don't exist or belong to someone else are simply missing from the result.
     */
    List<ClothingItem> findAllByIdInAndOwner_Id(Collection<UUID> ids, UUID ownerId);
}
