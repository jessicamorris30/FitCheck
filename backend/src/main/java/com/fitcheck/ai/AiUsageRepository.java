package com.fitcheck.ai;

import java.time.Instant;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Counting AI requests per user. */
public interface AiUsageRepository extends JpaRepository<AiUsage, UUID> {

    /** Requests by this user at or after the given moment (e.g. today's midnight UTC). */
    long countByUser_IdAndCreatedAtGreaterThanEqual(UUID userId, Instant since);
}
