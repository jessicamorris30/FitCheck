package com.fitcheck.auth;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Database access for {@link PasswordResetToken}. */
public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, UUID> {

    Optional<PasswordResetToken> findByTokenHash(String tokenHash);

    /** The most recent link for this user, for the resend cooldown. */
    Optional<PasswordResetToken> findTopByUser_IdOrderByCreatedAtDesc(UUID userId);

    /** After a successful reset, every other outstanding link for this user stops working too. */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update PasswordResetToken t set t.usedAt = :now where t.user.id = :userId and t.usedAt is null")
    int useUpAllForUser(@Param("userId") UUID userId, @Param("now") Instant now);
}
