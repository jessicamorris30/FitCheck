package com.fitcheck.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fitcheck.common.exception.BadRequestException;
import com.fitcheck.config.EmailProperties;
import com.fitcheck.user.User;
import com.fitcheck.user.UserRepository;
import com.fitcheck.user.UserService;
import com.jayway.jsonpath.JsonPath;
import java.time.Clock;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

/**
 * Email verification: the service rules and the HTTP endpoints.
 *
 * <p>These tests run inside a transaction that is rolled back, so it never "commits" and the
 * AFTER_COMMIT email listener never fires: no emails are sent while testing.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class EmailVerificationTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private EmailVerificationService service;

    @Autowired
    private EmailVerificationTokenRepository tokens;

    @Autowired
    private UserRepository users;

    @Autowired
    private UserService userService;

    @Autowired
    private ApplicationEventPublisher events;

    @Autowired
    private EmailProperties properties;

    private User newUser() {
        return users.saveAndFlush(new User("ev-" + System.nanoTime() + "@example.com", "hash"));
    }

    // ---- service ----

    @Test
    void validLinkVerifiesTheUser() {
        User user = newUser();
        String token = service.sendVerificationEmail(user);
        assertThat(user.isEmailVerified()).isFalse();

        User verified = service.verify(token);

        assertThat(verified.isEmailVerified()).isTrue();
        assertThat(tokens.findByTokenHash(SecureTokens.sha256(token)).orElseThrow().getUsedAt()).isNotNull();
    }

    @Test
    void clickingTheLinkTwiceStillSucceeds() {
        String token = service.sendVerificationEmail(newUser());
        service.verify(token);
        assertThat(service.verify(token).isEmailVerified()).isTrue();
    }

    @Test
    void unknownLinkIsRejected() {
        assertThatThrownBy(() -> service.verify("made-up")).isInstanceOf(BadRequestException.class);
    }

    @Test
    void expiredLinkIsRejected() {
        String token = service.sendVerificationEmail(newUser());
        var tomorrow = new EmailVerificationService(tokens, userService, events, properties,
                Clock.offset(Clock.systemUTC(), Duration.ofHours(25)));

        assertThatThrownBy(() -> tomorrow.verify(token)).isInstanceOf(BadRequestException.class);
    }

    @Test
    void tokenIsStoredOnlyAsHash() {
        String token = service.sendVerificationEmail(newUser());
        assertThat(tokens.findByTokenHash(token)).isEmpty();
        assertThat(tokens.findByTokenHash(SecureTokens.sha256(token))).isPresent();
    }

    // ---- HTTP ----

    @Test
    void newAccountStartsUnverified() throws Exception {
        register("fresh@example.com")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.user.emailVerified").value(false));
    }

    @Test
    void verifyEndpointConfirmsEmailAndRejectsBadLinks() throws Exception {
        String token = service.sendVerificationEmail(newUser());

        verify(token).andExpect(status().isOk()).andExpect(jsonPath("$.emailVerified").value(true));
        verify("made-up")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("This confirmation link is invalid or has expired"));
    }

    @Test
    void resendRequiresLoginAndHasACooldown() throws Exception {
        mvc.perform(post("/api/users/me/verification-email")).andExpect(status().isUnauthorized());

        String body = register("resend@example.com").andReturn().getResponse().getContentAsString();
        String accessToken = JsonPath.read(body, "$.accessToken");

        // Sign-up just sent an email, so asking again right away is too soon.
        mvc.perform(post("/api/users/me/verification-email").header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isTooManyRequests());
    }

    @Test
    void resendForVerifiedUserIsAConflict() throws Exception {
        String body = register("done@example.com").andReturn().getResponse().getContentAsString();
        String accessToken = JsonPath.read(body, "$.accessToken");
        users.findByEmailIgnoreCase("done@example.com").orElseThrow().markEmailVerified(java.time.Instant.now());

        mvc.perform(post("/api/users/me/verification-email").header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isConflict());
    }

    private ResultActions register(String email) throws Exception {
        return mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\",\"password\":\"correct-horse\"}"));
    }

    private ResultActions verify(String token) throws Exception {
        return mvc.perform(post("/api/auth/verify-email").contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\":\"" + token + "\"}"));
    }
}
