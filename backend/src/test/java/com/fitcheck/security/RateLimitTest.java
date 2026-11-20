package com.fitcheck.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

/**
 * Rate limits switched ON (they're off for the rest of the test suite). Each test uses its own
 * fake IP address (MockMvc lets us set it), so tests don't eat into each other's limits.
 */
@SpringBootTest(properties = "fitcheck.rate-limit.enabled=true")
@AutoConfigureMockMvc
@Transactional
class RateLimitTest {

    @Autowired
    private MockMvc mvc;

    private final String ip = "203.0.113." + (int) (Math.random() * 250);

    @Test
    void eleventhLoginFromOneIpInAMinuteIs429WithRetryAfter() throws Exception {
        String ipHere = "198.51.100." + (int) (Math.random() * 250);
        for (int i = 1; i <= 10; i++) {
            // Different (unknown) emails each time: this is the per-IP limit, not the per-account one.
            login(ipHere, "nobody" + i + "-" + UUID.randomUUID() + "@example.com", "whatever-1")
                    .andExpect(status().isUnauthorized());
        }
        login(ipHere, "another@example.com", "whatever-1")
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.startsWith("Too many attempts.")));

        // Another IP is unaffected.
        login(ip, "another@example.com", "whatever-1").andExpect(status().isUnauthorized());
    }

    @Test
    void fiveWrongPasswordsLockTheAccountEvenFromDifferentIps() throws Exception {
        String email = "target-" + UUID.randomUUID() + "@example.com";
        register(email);

        for (int i = 1; i <= 5; i++) {
            login("192.0.2." + i, email, "wrong-guess-" + i).andExpect(status().isUnauthorized());
        }
        // Even the RIGHT password is refused for now, from yet another IP.
        login("192.0.2.99", email, "right-password-1")
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("reset your password")));
    }

    @Test
    void successfulLoginClearsEarlierFailures() throws Exception {
        String email = "clumsy-" + UUID.randomUUID() + "@example.com";
        register(email);

        for (int i = 1; i <= 4; i++) {
            login(ip, email, "typo-" + i).andExpect(status().isUnauthorized());
        }
        login(ip, email, "right-password-1").andExpect(status().isOk());
        for (int i = 1; i <= 4; i++) {
            login(ip, email, "typo-again-" + i).andExpect(status().isUnauthorized()); // count started over
        }
        login(ip, email, "right-password-1").andExpect(status().isOk());
    }

    @Test
    void limiterWindowEndsAndCountsAgain() {
        AtomicReference<Instant> now = new AtomicReference<>(Instant.parse("2026-01-01T12:00:00Z"));
        Clock clock = new Clock() {
            @Override public ZoneOffset getZone() { return ZoneOffset.UTC; }
            @Override public Clock withZone(java.time.ZoneId zone) { return this; }
            @Override public Instant instant() { return now.get(); }
        };
        RateLimiter limiter = new RateLimiter(clock);

        assertThat(limiter.tryAcquire("k", 2, Duration.ofMinutes(1)).allowed()).isTrue();
        assertThat(limiter.tryAcquire("k", 2, Duration.ofMinutes(1)).allowed()).isTrue();
        RateLimiter.Decision third = limiter.tryAcquire("k", 2, Duration.ofMinutes(1));
        assertThat(third.allowed()).isFalse();
        assertThat(third.retryAfterSeconds()).isEqualTo(60);

        now.set(now.get().plusSeconds(61)); // the minute is over
        assertThat(limiter.tryAcquire("k", 2, Duration.ofMinutes(1)).allowed()).isTrue();
    }

    @Test
    void humanReadableWaitTimes() {
        assertThat(RateLimitFilter.humanize(1)).isEqualTo("1 second");
        assertThat(RateLimitFilter.humanize(45)).isEqualTo("45 seconds");
        assertThat(RateLimitFilter.humanize(61)).isEqualTo("2 minutes");
        assertThat(RateLimitFilter.humanize(900)).isEqualTo("15 minutes");
    }

    // ---- helpers ----

    private void register(String email) throws Exception {
        mvc.perform(post("/api/auth/register").with(r -> { r.setRemoteAddr("10.0.0." + (int) (Math.random() * 250)); return r; })
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"right-password-1\"}"))
                .andExpect(status().isCreated());
    }

    private ResultActions login(String fromIp, String email, String password) throws Exception {
        return mvc.perform(post("/api/auth/login").with(r -> { r.setRemoteAddr(fromIp); return r; })
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}"));
    }
}
