package com.fitcheck.style;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StyleImageRepository extends JpaRepository<StyleImage, UUID> {

    /** A user's style photos, oldest first (the order they were added). */
    List<StyleImage> findAllByOwner_IdOrderByCreatedAtAscIdAsc(UUID ownerId);

    Optional<StyleImage> findByIdAndOwner_Id(UUID id, UUID ownerId);

    long countByOwner_Id(UUID ownerId);
}
