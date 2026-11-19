package com.fitcheck.auth;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Database access for {@link EmailVerificationToken}. */
public interface EmailVerificationTokenRepository extends JpaRepository<EmailVerificationToken, UUID> {

    Optional<EmailVerificationToken> findByTokenHash(String tokenHash);

    /**
     * The most recent link sent to this user, used for the resend cooldown.
     * {@code User_Id} walks the relationship: the token's user, then that user's id.
     */
    Optional<EmailVerificationToken> findTopByUser_IdOrderByCreatedAtDesc(UUID userId);
}
