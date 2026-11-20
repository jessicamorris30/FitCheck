package com.fitcheck.outfit;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Database access for {@link Outfit}. Like clothing items, every lookup takes the owner's id.
 *
 * <p>{@code @EntityGraph} avoids the "N+1 queries" problem: without it, loading 10 outfits would
 * run 1 query for the outfits, then 1 more per outfit for its items, then 1 more per item for its
 * clothing details. The entity graph tells Hibernate to fetch outfits, their items and the
 * clothing in one go.
 */
public interface OutfitRepository extends JpaRepository<Outfit, UUID> {

    /** All of an owner's outfits, most recently changed first. */
    @EntityGraph(attributePaths = {"items", "items.clothingItem"})
    List<Outfit> findAllByOwner_IdOrderByUpdatedAtDesc(UUID ownerId);

    /** One outfit, only if it belongs to this owner. */
    @EntityGraph(attributePaths = {"items", "items.clothingItem"})
    Optional<Outfit> findByIdAndOwner_Id(UUID id, UUID ownerId);

    long countByOwner_Id(UUID ownerId);
}
