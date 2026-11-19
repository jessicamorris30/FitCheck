package com.fitcheck.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fitcheck.config.JwtProperties;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Plain unit tests: no Spring, no database. JwtService is created by hand with test settings
 * and a fixed clock, so we can "travel in time" to check expiry.
 */
class JwtServiceTest {

    private static final String SECRET = Base64.getEncoder().encodeToString(new byte[48]);
    private static final String OTHER_SECRET = Base64.getEncoder().encodeToString("x".repeat(48).getBytes());
    private static final Instant NOW = Instant.parse("2026-01-01T12:00:00Z");
    private static final UUID USER_ID = UUID.randomUUID();

    private static JwtService serviceAt(Instant now, String secret) {
        JwtProperties props = new JwtProperties(secret, Duration.ofMinutes(15), Duration.ofDays(14),
                "fitcheck_refresh", "/api/auth", true);
        return new JwtService(props, Clock.fixed(now, ZoneOffset.UTC));
    }

    @Test
    void createdTokenCanBeParsedBack() {
        JwtService jwt = serviceAt(NOW, SECRET);
        String token = jwt.createAccessToken(USER_ID, "ada@example.com");

        assertThat(token.split("\\.")).hasSize(3); // header.payload.signature
        assertThat(jwt.parseAccessToken(token))
                .contains(new AuthenticatedUser(USER_ID, "ada@example.com"));
    }

    @Test
    void tokenIsRejectedAfterItExpires() {
        String token = serviceAt(NOW, SECRET).createAccessToken(USER_ID, "ada@example.com");

        assertThat(serviceAt(NOW.plus(Duration.ofMinutes(14)), SECRET).parseAccessToken(token)).isPresent();
        assertThat(serviceAt(NOW.plus(Duration.ofMinutes(16)), SECRET).parseAccessToken(token)).isEmpty();
    }

    @Test
    void tamperedTokenIsRejected() {
        JwtService jwt = serviceAt(NOW, SECRET);
        String[] parts = jwt.createAccessToken(USER_ID, "ada@example.com").split("\\.");

        // Swap in a payload claiming to be a different user, keeping the original signature.
        String fakePayload = Base64.getUrlEncoder().withoutPadding().encodeToString(
                ("{\"iss\":\"fitcheck\",\"sub\":\"" + UUID.randomUUID() + "\"}").getBytes());
        String forged = parts[0] + "." + fakePayload + "." + parts[2];

        assertThat(jwt.parseAccessToken(forged)).isEmpty();
    }

    @Test
    void tokenSignedWithAnotherKeyIsRejected() {
        String token = serviceAt(NOW, OTHER_SECRET).createAccessToken(USER_ID, "ada@example.com");
        assertThat(serviceAt(NOW, SECRET).parseAccessToken(token)).isEmpty();
    }

    @Test
    void garbageIsRejected() {
        assertThat(serviceAt(NOW, SECRET).parseAccessToken("not.a.jwt")).isEmpty();
        assertThat(serviceAt(NOW, SECRET).parseAccessToken("")).isEmpty();
    }

    @Test
    void missingOrShortSecretFailsFast() {
        assertThatThrownBy(() -> serviceAt(NOW, "")).hasMessageContaining("JWT_SECRET");
        assertThatThrownBy(() -> serviceAt(NOW, Base64.getEncoder().encodeToString(new byte[16])))
                .hasMessageContaining("at least 32 bytes");
    }
}
