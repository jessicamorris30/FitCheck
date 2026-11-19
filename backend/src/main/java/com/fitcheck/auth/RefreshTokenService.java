package com.fitcheck.auth;

import com.fitcheck.common.exception.UnauthorizedException;
import com.fitcheck.config.JwtProperties;
import com.fitcheck.user.User;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
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

    /** Cryptographically strong randomness; never use java.util.Random for secrets. */
    private static final SecureRandom RANDOM = new SecureRandom();

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
        RefreshToken current = tokens.findByTokenHash(hash(rawToken))
                .orElseThrow(() -> new UnauthorizedException(INVALID));

        if (current.isRevoked()) {
            int revoked = tokens.revokeFamily(current.getFamilyId(), now);
            log.warn("Refresh token reuse detected for user {}; revoked {} token(s) in family {}",
                    current.getUser().getId(), revoked, current.getFamilyId());
            throw new UnauthorizedException(INVALID);
        }
        if (current.isExpired(now)) {
            throw new UnauthorizedException(INVALID);
        }

        Created next = create(current.getUser(), current.getFamilyId());
        current.rotateTo(next.entity().getId(), now);
        return next.issued();
    }

    /** Logout: retires the whole family. Unknown or already-revoked tokens are simply ignored. */
    @Transactional
    public void revoke(String rawToken) {
        tokens.findByTokenHash(hash(rawToken))
                .ifPresent(t -> tokens.revokeFamily(t.getFamilyId(), clock.instant()));
    }

    private record Created(RefreshToken entity, IssuedToken issued) {
    }

    private Created create(User user, UUID familyId) {
        String raw = newRawToken();
        Instant expiresAt = clock.instant().plus(properties.refreshTokenTtl());
        RefreshToken saved = tokens.save(new RefreshToken(user, hash(raw), familyId, expiresAt));
        return new Created(saved, new IssuedToken(raw, user));
    }

    /** 32 random bytes (256 bits) as URL-safe text, which is impossible to guess. */
    private static String newRawToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /**
     * SHA-256, not BCrypt: passwords need a slow hash because people pick guessable ones, but
     * these tokens are 256 random bits, so a fast hash is already safe. It also gives the same
     * output every time, which is what lets us look tokens up by hash.
     */
    static String hash(String rawToken) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
