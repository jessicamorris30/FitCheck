package com.fitcheck.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fitcheck.common.exception.BadRequestException;
import com.fitcheck.config.EmailProperties;
import com.fitcheck.user.UserRepository;
import jakarta.servlet.http.Cookie;
import java.time.Clock;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

/**
 * Forgot / reset password. Rolled back after each test, so the AFTER_COMMIT email listener never
 * fires; tests get the raw token straight from the service instead of from an email.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class PasswordResetTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private PasswordResetService service;

    @Autowired
    private PasswordResetTokenRepository tokens;

    @Autowired
    private UserRepository users;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private RefreshTokenService refreshTokens;

    @Autowired
    private ApplicationEventPublisher events;

    @Autowired
    private EmailProperties properties;

    private String register(String email) throws Exception {
        return mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"old-password-1\"}"))
                .andReturn().getResponse().getCookie("fitcheck_refresh").getValue();
    }

    @Test
    void unknownEmailGetsTheSameAnswerAndNothingHappens() throws Exception {
        long before = tokens.count();
        forgot("nobody-" + System.nanoTime() + "@example.com").andExpect(status().isNoContent());
        assertThat(tokens.count()).isEqualTo(before);
    }

    @Test
    void knownEmailGetsTheSameAnswer() throws Exception {
        register("known@example.com");
        forgot("KNOWN@example.com").andExpect(status().isNoContent());
    }

    @Test
    void resetLinkSetsTheNewPasswordAndSignsOutEverywhere() throws Exception {
        String oldSession = register("reset@example.com");
        String token = service.requestReset("reset@example.com").orElseThrow();

        reset(token, "brand-new-pass").andExpect(status().isNoContent());

        login("reset@example.com", "old-password-1").andExpect(status().isUnauthorized());
        login("reset@example.com", "brand-new-pass").andExpect(status().isOk());
        mvc.perform(post("/api/auth/refresh").cookie(new Cookie("fitcheck_refresh", oldSession)))
                .andExpect(status().isUnauthorized());
        // Opening a link from the inbox proves the email works, so it counts as confirmed.
        assertThat(users.findByEmailIgnoreCase("reset@example.com").orElseThrow().isEmailVerified()).isTrue();
    }

    @Test
    void aLinkWorksOnlyOnce() throws Exception {
        register("once@example.com");
        String token = service.requestReset("once@example.com").orElseThrow();

        reset(token, "brand-new-pass").andExpect(status().isNoContent());
        reset(token, "another-pass-9")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("This reset link is invalid or has expired. Please request a new one."));
    }

    @Test
    void expiredLinkIsRejected() throws Exception {
        register("late@example.com");
        String token = service.requestReset("late@example.com").orElseThrow();

        var twoHoursLater = new PasswordResetService(tokens, users, passwordEncoder, refreshTokens, events, properties,
                Clock.offset(Clock.systemUTC(), Duration.ofHours(2)));
        assertThatThrownBy(() -> twoHoursLater.resetPassword(token, "brand-new-pass"))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void repeatedRequestsWithinTheCooldownDontSendAnotherEmail() throws Exception {
        register("spam@example.com");
        assertThat(service.requestReset("spam@example.com")).isPresent();
        assertThat(service.requestReset("spam@example.com")).isEmpty();
    }

    @Test
    void validatesInput() throws Exception {
        forgot("not-an-email").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.email").value("Must be a valid email address"));
        reset("made-up", "short").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.newPassword").value("Password must be 8-72 characters"));
        reset("made-up", "long-enough-1").andExpect(status().isBadRequest());
    }

    // ---- helpers ----

    private ResultActions forgot(String email) throws Exception {
        return mvc.perform(post("/api/auth/forgot-password").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\"}"));
    }

    private ResultActions reset(String token, String password) throws Exception {
        return mvc.perform(post("/api/auth/reset-password").contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\":\"" + token + "\",\"newPassword\":\"" + password + "\"}"));
    }

    private ResultActions login(String email, String password) throws Exception {
        return mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}"));
    }
}
