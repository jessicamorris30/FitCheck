package com.fitcheck.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fitcheck.user.User;
import com.jayway.jsonpath.JsonPath;
import com.fitcheck.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

/**
 * End-to-end tests for POST /api/auth/register through the real controller, service, security
 * rules and Postgres. @Transactional rolls each test back so the dev database stays clean.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AuthControllerTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private UserRepository users;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Test
    void registersUserAndStoresHashedPassword() throws Exception {
        register("""
                { "email": "  New.User@Example.com ", "password": "correct-horse" }
                """)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.email").value("new.user@example.com"))
                .andExpect(jsonPath("$.createdAt").isNotEmpty())
                .andExpect(jsonPath("$.passwordHash").doesNotExist());

        User saved = users.findByEmailIgnoreCase("new.user@example.com").orElseThrow();
        assertThat(saved.getPasswordHash()).isNotEqualTo("correct-horse").startsWith("$2");
        assertThat(passwordEncoder.matches("correct-horse", saved.getPasswordHash())).isTrue();
    }

    @Test
    void rejectsDuplicateEmailInAnyCase() throws Exception {
        register("""
                { "email": "taken@example.com", "password": "password-1" }
                """).andExpect(status().isCreated());

        register("""
                { "email": "TAKEN@example.com", "password": "password-2" }
                """)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("An account with this email already exists"));
    }

    @Test
    void rejectsInvalidInputWithFieldErrors() throws Exception {
        register("""
                { "email": "not-an-email", "password": "short" }
                """)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.email").value("Must be a valid email address"))
                .andExpect(jsonPath("$.fieldErrors.password").value("Password must be 8-72 characters"));
    }

    @Test
    void rejectsMalformedJson() throws Exception {
        register("{ this is not json")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Request body is missing or malformed"));
    }

    // ---- Login + /me (step 4) ----

    @Test
    void loginReturnsAccessTokenThatUnlocksMe() throws Exception {
        register("""
                { "email": "login@example.com", "password": "correct-horse" }
                """).andExpect(status().isCreated());

        String body = login("""
                { "email": " LOGIN@example.com ", "password": "correct-horse" }
                """)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.expiresIn").value(900))
                .andExpect(jsonPath("$.user.email").value("login@example.com"))
                .andReturn().getResponse().getContentAsString();
        String token = JsonPath.read(body, "$.accessToken");

        mvc.perform(get("/api/users/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("login@example.com"));
    }

    @Test
    void wrongPasswordAndUnknownEmailGetTheSame401() throws Exception {
        register("""
                { "email": "real@example.com", "password": "correct-horse" }
                """).andExpect(status().isCreated());

        login("""
                { "email": "real@example.com", "password": "wrong-password" }
                """)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid email or password"));

        login("""
                { "email": "ghost@example.com", "password": "whatever-123" }
                """)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid email or password"));
    }

    @Test
    void meRejectsMissingOrInvalidToken() throws Exception {
        mvc.perform(get("/api/users/me")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/users/me").header("Authorization", "Bearer not-a-real-token"))
                .andExpect(status().isUnauthorized());
    }

    private ResultActions login(String json) throws Exception {
        return mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private ResultActions register(String json) throws Exception {
        return mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(json));
    }
}
