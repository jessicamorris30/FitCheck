package com.fitcheck.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Typed access to the {@code fitcheck.jwt.*} settings in application.yml.
 *
 * <p>Spring Boot reads the YAML and fills in this record ("binding"); kebab-case keys map to
 * camelCase fields ({@code access-token-ttl} → {@code accessTokenTtl}) and values like
 * {@code 15m} or {@code 14d} become {@link Duration}s automatically.
 *
 * @param secret              base64-encoded HMAC key used to sign access tokens (≥ 32 bytes)
 * @param accessTokenTtl      how long an access token is valid
 * @param refreshTokenTtl     how long a refresh token is valid (step 5)
 * @param refreshCookieName   name of the refresh-token cookie (step 5)
 * @param refreshCookiePath   path the cookie is limited to (step 5)
 * @param refreshCookieSecure whether the cookie requires HTTPS (step 5)
 */
@ConfigurationProperties("fitcheck.jwt")
public record JwtProperties(
        String secret,
        Duration accessTokenTtl,
        Duration refreshTokenTtl,
        String refreshCookieName,
        String refreshCookiePath,
        boolean refreshCookieSecure) {
}
