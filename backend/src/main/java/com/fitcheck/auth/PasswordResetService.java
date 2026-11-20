package com.fitcheck.auth;

import com.fitcheck.common.exception.BadRequestException;
import com.fitcheck.config.EmailProperties;
import com.fitcheck.user.User;
import com.fitcheck.user.UserRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.Locale;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * "Forgot password": email a one-time link, then let whoever opens it choose a new password.
 *
 * <ol>
 *   <li>{@link #requestReset}: if the email has an account, create a random token (hash stored,
 *       valid 1 hour) and email {@code <frontend>/reset-password?token=...}.
 *   <li>{@link #resetPassword}: the token proves the person can read that inbox, so they may set
 *       a new password. The link is single-use, and every session is signed out.
 * </ol>
 *
 * <p><b>Privacy rule:</b> the response to step 1 is identical whether or not the email has an
 * account. Otherwise this form would let anyone check which emails are registered.
 */
@Service
public class PasswordResetService {

    private static final Logger log = LoggerFactory.getLogger(PasswordResetService.class);
    private static final String INVALID_LINK = "This reset link is invalid or has expired. Please request a new one.";

    private final PasswordResetTokenRepository tokens;
    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final RefreshTokenService refreshTokens;
    private final ApplicationEventPublisher events;
    private final EmailProperties properties;
    private final Clock clock;

    public PasswordResetService(PasswordResetTokenRepository tokens, UserRepository users,
            PasswordEncoder passwordEncoder, RefreshTokenService refreshTokens, ApplicationEventPublisher events,
            EmailProperties properties, Clock clock) {
        this.tokens = tokens;
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.refreshTokens = refreshTokens;
        this.events = events;
        this.properties = properties;
        this.clock = clock;
    }

    /**
     * Emails a reset link if this address has an account. Never says whether it does.
     *
     * @return the raw token if one was created (for tests; the controller ignores it)
     */
    @Transactional
    public Optional<String> requestReset(String email) {
        Optional<User> found = users.findByEmailIgnoreCase(email.trim().toLowerCase(Locale.ROOT));
        if (found.isEmpty()) {
            return Optional.empty(); // same response as success: nothing is revealed
        }
        User user = found.get();

        // Quietly ignore repeats within the cooldown (no 429 either: that would reveal the account exists).
        boolean tooSoon = tokens.findTopByUser_IdOrderByCreatedAtDesc(user.getId())
                .map(last -> last.getCreatedAt().plus(properties.resendCooldown()).isAfter(clock.instant()))
                .orElse(false);
        if (tooSoon) {
            log.info("Password reset requested again too soon for user {}; not sending another email", user.getId());
            return Optional.empty();
        }

        String raw = SecureTokens.newToken();
        tokens.save(new PasswordResetToken(user, SecureTokens.sha256(raw),
                clock.instant().plus(properties.passwordResetTtl())));

        String resetUrl = UriComponentsBuilder.fromUriString(properties.frontendUrl())
                .path("/reset-password")
                .queryParam("token", raw)
                .toUriString();
        events.publishEvent(new PasswordResetRequested(user.getEmail(), resetUrl));
        return Optional.of(raw);
    }

    /**
     * Sets a new password using a reset link's token.
     *
     * <p>Also: marks the email as confirmed (they just proved they can read it), uses up every
     * other outstanding reset link, and signs the account out everywhere (in case someone else
     * was logged in with the old password). The user then logs in with the new password.
     *
     * @throws BadRequestException if the token is unknown, already used or expired
     */
    @Transactional
    public void resetPassword(String rawToken, String newPassword) {
        Instant now = clock.instant();
        PasswordResetToken token = tokens.findByTokenHash(SecureTokens.sha256(rawToken))
                .filter(t -> t.isUsable(now))
                .orElseThrow(() -> new BadRequestException(INVALID_LINK));

        User user = token.getUser();
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        user.markEmailVerified(now);
        token.markUsed(now);

        tokens.useUpAllForUser(user.getId(), now);
        refreshTokens.revokeAll(user.getId());
    }
}
