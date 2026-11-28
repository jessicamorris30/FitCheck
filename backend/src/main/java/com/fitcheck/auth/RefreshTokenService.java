package com.fitcheck.auth;

import com.fitcheck.common.exception.UnauthorizedException;
import com.fitcheck.config.JwtProperties;
import com.fitcheck.user.User;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Issues, rotates and revokes refresh tokens. See docs/AUTH.md for the full design.
 *
 * <p>Rotation: each refresh token works exactly once. Using it returns a new one and retires the
 * old one. If a retired token shows up again, two parties have the same token (one of them
 * stole it), and we can't tell which is the real user, so the whole family is revoked and both
 * must log in again.
 */
@Service
public class RefreshTokenService {

    private static final Logger log = LoggerFactory.getLogger(RefreshTokenService.class);
    private static final String INVALID = "Invalid or expired session";
    /** How long a just-rotated token still works, for two tabs refreshing together. */
    static final Duration TWO_TAB_GRACE = Duration.ofSeconds(10);

    private final RefreshTokenRepository tokens;
    private final JwtProperties properties;
    private final Clock clock;

    public RefreshTokenService(RefreshTokenRepository tokens, JwtProperties properties, Clock clock) {
        this.tokens = tokens;
        this.properties = properties;
        this.clock = clock;
    }

    /** A freshly issued token. {@code value} goes into the cookie and is never stored anywhere. */
    public record IssuedToken(String value, User user) {
    }

    /** Starts a new family for a fresh login or registration. */
    @Transactional
    public IssuedToken issue(User user) {
        return create(user, UUID.randomUUID()).issued();
    }

    /**
     * Exchanges a valid refresh token for a new one in the same family.
     *
     * <p>{@code noRollbackFor}: normally an exception undoes everything the method wrote. But when
     * we detect reuse we revoke the family and THEN throw, and that revocation must be saved.
     *
     * @throws UnauthorizedException if the token is unknown, expired, or already used (reuse)
     */
    @Transactional(noRollbackFor = UnauthorizedException.class)
    public IssuedToken rotate(String rawToken) {
        Instant now = clock.instant();
        RefreshToken current = tokens.findByTokenHash(SecureTokens.sha256(rawToken))
                .orElseThrow(() -> new UnauthorizedException(INVALID));

        if (current.isRevoked() && isTwoTabRace(current, now)) {
            // Not a thief: another tab of the same browser refreshed a moment ago (see TWO_TAB_GRACE).
            log.info("Refresh token reused within {}s of rotation (two tabs?); issuing another token in family {}",
                    TWO_TAB_GRACE.toSeconds(), current.getFamilyId());
            return create(current.getUser(), current.getFamilyId()).issued();
        }
        if (current.isRevoked()) {
            int revoked = tokens.revokeFamily(current.getFamilyId(), now);
            log.warn("Refresh token reuse detected for user {}; revoked {} token(s) in family {}",
                    current.getUser().getId(), revoked, current.getFamilyId());
            throw new UnauthorizedException(INVALID);
        }
        if (current.isExpired(now)) {
            throw new UnauthorizedException(INVALID);
        }
        if (current.getUser().isFrozen()) {
            // Suspended by an admin: end this session for good (the login page then explains why).
            tokens.revokeFamily(current.getFamilyId(), now);
            throw new UnauthorizedException(INVALID);
        }

        Created next = create(current.getUser(), current.getFamilyId());
        current.rotateTo(next.entity().getId(), now);
        return next.issued();
    }

    /**
     * Was this token swapped for a new one only moments ago, and is that new one still active?
     *
     * <p>All tabs of a browser share ONE cookie. When two tabs refresh at the same moment (e.g. an
     * email link opened in a new tab), both send the same token: the first swaps it, and the second
     * would look exactly like a replayed stolen token and log the user out everywhere. So the
     * old token is accepted for a few more seconds, but only while its replacement is still active:
     * after a logout, password change or detected theft the replacement is revoked too, and the old
     * token is refused. A thief would need the token AND to use it within seconds of the real user.
     */
    private boolean isTwoTabRace(RefreshToken token, Instant now) {
        return token.getReplacedBy() != null
                && !token.isExpired(now)
                && now.isBefore(token.getRevokedAt().plus(TWO_TAB_GRACE))
                && tokens.findById(token.getReplacedBy()).filter(next -> !next.isRevoked()).isPresent();
    }

    /** Logout: retires the whole family. Unknown or already-revoked tokens are simply ignored. */
    @Transactional
    public void revoke(String rawToken) {
        tokens.findByTokenHash(SecureTokens.sha256(rawToken))
                .ifPresent(t -> tokens.revokeFamily(t.getFamilyId(), clock.instant()));
    }

    /** Signs this user out everywhere: every device's refresh token stops working. */
    @Transactional
    public int revokeAll(UUID userId) {
        return tokens.revokeAllForUser(userId, clock.instant());
    }

    private record Created(RefreshToken entity, IssuedToken issued) {
    }

    private Created create(User user, UUID familyId) {
        String raw = SecureTokens.newToken();
        Instant expiresAt = clock.instant().plus(properties.refreshTokenTtl());
        RefreshToken saved = tokens.save(new RefreshToken(user, SecureTokens.sha256(raw), familyId, expiresAt));
        return new Created(saved, new IssuedToken(raw, user));
    }
}
