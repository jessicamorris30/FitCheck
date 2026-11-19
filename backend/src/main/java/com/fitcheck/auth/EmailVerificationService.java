package com.fitcheck.auth;

import com.fitcheck.common.exception.BadRequestException;
import com.fitcheck.common.exception.ConflictException;
import com.fitcheck.common.exception.TooManyRequestsException;
import com.fitcheck.config.EmailProperties;
import com.fitcheck.user.User;
import com.fitcheck.user.UserService;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * Confirming that a user really owns their email address.
 *
 * <ol>
 *   <li>Sign-up (or "resend") creates a random token, stores its hash, and requests an email
 *       containing {@code <frontend>/verify-email?token=...}.
 *   <li>The user clicks the link; the React page sends the token to {@code POST /api/auth/verify-email}.
 *   <li>{@link #verify} finds the token by hash and marks the user verified.
 * </ol>
 */
@Service
public class EmailVerificationService {

    private static final String INVALID_LINK = "This confirmation link is invalid or has expired";

    private final EmailVerificationTokenRepository tokens;
    private final UserService userService;
    private final ApplicationEventPublisher events;
    private final EmailProperties properties;
    private final Clock clock;

    public EmailVerificationService(EmailVerificationTokenRepository tokens, UserService userService,
            ApplicationEventPublisher events, EmailProperties properties, Clock clock) {
        this.tokens = tokens;
        this.userService = userService;
        this.events = events;
        this.properties = properties;
        this.clock = clock;
    }

    /**
     * Creates a fresh link for this user and asks for the email to be sent.
     *
     * @return the raw token (handy for tests; production code ignores it)
     */
    @Transactional
    public String sendVerificationEmail(User user) {
        String raw = SecureTokens.newToken();
        Instant expiresAt = clock.instant().plus(properties.verificationTtl());
        tokens.save(new EmailVerificationToken(user, SecureTokens.sha256(raw), expiresAt));

        String verifyUrl = UriComponentsBuilder.fromUriString(properties.frontendUrl())
                .path("/verify-email")
                .queryParam("token", raw)
                .toUriString();
        events.publishEvent(new VerificationEmailRequested(user.getEmail(), verifyUrl));
        return raw;
    }

    /**
     * "Resend confirmation email" for the logged-in user.
     *
     * @throws ConflictException        if the email is already verified
     * @throws TooManyRequestsException if the last email went out less than the cooldown ago
     */
    @Transactional
    public void resend(UUID userId) {
        User user = userService.getById(userId);
        if (user.isEmailVerified()) {
            throw new ConflictException("Your email is already confirmed");
        }
        tokens.findTopByUser_IdOrderByCreatedAtDesc(userId)
                .filter(last -> last.getCreatedAt().plus(properties.resendCooldown()).isAfter(clock.instant()))
                .ifPresent(last -> {
                    throw new TooManyRequestsException("Please wait a minute before requesting another email");
                });
        sendVerificationEmail(user);
    }

    /**
     * Confirms the email that this token was sent to.
     *
     * <p>Clicking the same valid link twice is fine and returns success again: email apps and
     * browsers sometimes open links twice, and the user is verified either way.
     *
     * @throws BadRequestException if the token is unknown or expired
     */
    @Transactional
    public User verify(String rawToken) {
        Instant now = clock.instant();
        EmailVerificationToken token = tokens.findByTokenHash(SecureTokens.sha256(rawToken))
                .filter(t -> t.getUsedAt() != null || !t.isExpired(now))
                .orElseThrow(() -> new BadRequestException(INVALID_LINK));

        token.markUsed(now);
        User user = token.getUser();
        user.markEmailVerified(now);
        return user;
    }
}
