package com.fitcheck.auth;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

/**
 * Helpers for random one-time tokens (refresh tokens, email verification links).
 * Both work the same way: generate a random value, give it to the user, store only its hash.
 */
public final class SecureTokens {

    /** Cryptographically strong randomness; never use java.util.Random for secrets. */
    private static final SecureRandom RANDOM = new SecureRandom();

    private SecureTokens() {
    }

    /** 32 random bytes (256 bits) as URL-safe text, which is impossible to guess. */
    public static String newToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /**
     * SHA-256 as 64 hex characters. Not BCrypt: passwords need a slow hash because people pick
     * guessable ones, but these tokens are 256 random bits, so a fast hash is already safe. It
     * also gives the same output every time, which is what lets us look tokens up by hash.
     */
    public static String sha256(String token) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
