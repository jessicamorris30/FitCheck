package com.fitcheck.calendar;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PlannedOutfitRepository extends JpaRepository<PlannedOutfit, UUID> {

    /**
     * An owner's calendar between two days (both included), by day, then in the order added.
     * The entity graph loads each outfit with its clothes in the same query (no query per outfit).
     */
    @EntityGraph(attributePaths = {"outfit", "outfit.items", "outfit.items.clothingItem"})
    List<PlannedOutfit> findAllByOwner_IdAndDayBetweenOrderByDayAscCreatedAtAscIdAsc(
            UUID ownerId, LocalDate from, LocalDate to);

    /** One calendar entry, only if it belongs to this owner. */
    Optional<PlannedOutfit> findByIdAndOwner_Id(UUID id, UUID ownerId);

    /** Every day one outfit is on, earliest first. (Callers check the outfit is the user's first.) */
    List<PlannedOutfit> findAllByOutfit_IdOrderByDayAsc(UUID outfitId);

    boolean existsByOutfit_IdAndDay(UUID outfitId, LocalDate day);

    long countByOwner_IdAndDay(UUID ownerId, LocalDate day);
}
