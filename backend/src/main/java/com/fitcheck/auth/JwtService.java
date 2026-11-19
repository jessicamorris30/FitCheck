package com.fitcheck.auth;

import com.fitcheck.config.JwtProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import java.time.Clock;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;
import java.util.UUID;
import javax.crypto.SecretKey;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * Creates and checks JWT access tokens.
 *
 * <p>A JWT is three base64 parts joined by dots: {@code header.payload.signature}.
 * <ul>
 *   <li>header: which algorithm signed it (HS256 here),
 *   <li>payload ("claims"): who it's for ({@code sub} = user id), when it was issued and expires,
 *   <li>signature: an HMAC of the first two parts made with our secret key.
 * </ul>
 * Anyone can read the payload (it's only encoded, not encrypted), so never put secrets in it.
 * But nobody can change it without the key, because the signature would no longer match.
 */
@Service
public class JwtService {

    private static final String ISSUER = "fitcheck";
    private static final String EMAIL_CLAIM = "email";

    private final SecretKey key;
    private final JwtProperties properties;
    private final Clock clock;

    @Autowired
    public JwtService(JwtProperties properties) {
        this(properties, Clock.systemUTC());
    }

    /** Lets tests use a fixed clock to check expiry. */
    JwtService(JwtProperties properties, Clock clock) {
        this.properties = properties;
        this.clock = clock;
        this.key = buildKey(properties.secret());
    }

    /** Issues a signed access token for this user, valid for {@code fitcheck.jwt.access-token-ttl}. */
    public String createAccessToken(UUID userId, String email) {
        Instant now = clock.instant();
        return Jwts.builder()
                .issuer(ISSUER)
                .subject(userId.toString())
                .claim(EMAIL_CLAIM, email)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(properties.accessTokenTtl())))
                .signWith(key)
                .compact();
    }

    /**
     * Checks the signature, issuer and expiry. Returns the user if everything is valid,
     * or empty for any bad token (expired, tampered with, signed with another key, garbage).
     */
    public Optional<AuthenticatedUser> parseAccessToken(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(key)
                    .requireIssuer(ISSUER)
                    .clock(() -> Date.from(clock.instant()))
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            return Optional.of(new AuthenticatedUser(
                    UUID.fromString(claims.getSubject()), claims.get(EMAIL_CLAIM, String.class)));
        } catch (JwtException | IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    /** Seconds an access token lives; sent to the client as {@code expiresIn}. */
    public long accessTokenTtlSeconds() {
        return properties.accessTokenTtl().toSeconds();
    }

    /** Fails at startup with a clear message instead of failing on the first login. */
    private static SecretKey buildKey(String base64Secret) {
        if (base64Secret == null || base64Secret.isBlank()) {
            throw new IllegalStateException("fitcheck.jwt.secret is not set. Set JWT_SECRET in your .env file.");
        }
        byte[] bytes = Decoders.BASE64.decode(base64Secret);
        if (bytes.length < 32) {
            throw new IllegalStateException("JWT_SECRET must decode to at least 32 bytes (256 bits).");
        }
        return Keys.hmacShaKeyFor(bytes);
    }
}
