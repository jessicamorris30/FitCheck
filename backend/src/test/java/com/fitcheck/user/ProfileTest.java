package com.fitcheck.user;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fitcheck.auth.JwtService;
import com.jayway.jsonpath.JsonPath;
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
 * Phase 7.1: usernames and names. Signing up with them, logging in with a username,
 * "finish your profile" for older accounts, editing, and the live username check.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ProfileTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private UserRepository users;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtService jwt;

    // ---- sign-up ----

    @Test
    void signUpNeedsUsernameAndBothNames() throws Exception {
        register("{\"email\":\"a@example.com\",\"password\":\"long-enough-1\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.username").value("Username is required"))
                .andExpect(jsonPath("$.fieldErrors.firstName").value("First name is required"))
                .andExpect(jsonPath("$.fieldErrors.lastName").value("Last name is required"));
    }

    @Test
    void usernamesFollowTheRules() throws Exception {
        for (String bad : new String[] {"ab", "has space", "me@home", "_starts", ".dot", "émile", "x".repeat(31)}) {
            register(signUp("rules-" + bad.hashCode() + "@example.com", bad))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.fieldErrors.username").exists());
        }
        register(signUp("reserved@example.com", "Admin"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.username").value("That username isn't available"));
        register(signUp("ok@example.com", "jess_m.2026")).andExpect(status().isCreated());
    }

    @Test
    void aUsernameCanOnlyBeTakenOnceInAnyLetterCase() throws Exception {
        register(signUp("first@example.com", "stylequeen")).andExpect(status().isCreated());

        register(signUp("second@example.com", "StyleQueen"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.username").value("That username is already taken"));
    }

    @Test
    void namesKeepAccentsAndApostrophesButNotControlCharacters() throws Exception {
        register("{\"email\":\"jose@example.com\",\"password\":\"long-enough-1\",\"username\":\"jose\","
                        + "\"firstName\":\"José\",\"lastName\":\"O'Brien-Nguyễn\"}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.user.firstName").value("José"))
                .andExpect(jsonPath("$.user.lastName").value("O'Brien-Nguyễn"));

        register("{\"email\":\"ctrl@example.com\",\"password\":\"long-enough-1\",\"username\":\"ctrl\","
                        + "\"firstName\":\"Bad\\u0007Bell\",\"lastName\":\"X\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.firstName").exists());
    }

    // ---- login ----

    @Test
    void logInWithUsernameInAnyCaseOrWithEmail() throws Exception {
        register(signUp("both@example.com", "both.ways")).andExpect(status().isCreated());

        login("{\"login\":\"Both.Ways\",\"password\":\"long-enough-1\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.username").value("both.ways"));
        login("{\"login\":\"BOTH@example.com\",\"password\":\"long-enough-1\"}").andExpect(status().isOk());
        // The old field name still works.
        login("{\"email\":\"both@example.com\",\"password\":\"long-enough-1\"}").andExpect(status().isOk());

        login("{\"login\":\"both.ways\",\"password\":\"wrong-password\"}")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid email, username or password"));
        login("{\"login\":\"nobody.here\",\"password\":\"long-enough-1\"}")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid email, username or password"));
    }

    // ---- accounts from before Phase 7 ----

    @Test
    void olderAccountsAreIncompleteUntilTheyFinishTheirProfile() throws Exception {
        User old = users.saveAndFlush(new User("old@example.com", passwordEncoder.encode("long-enough-1")));
        String bearer = "Bearer " + jwt.createAccessToken(old.getId(), old.getEmail());

        mvc.perform(get("/api/users/me").header("Authorization", bearer))
                .andExpect(jsonPath("$.profileComplete").value(false))
                .andExpect(jsonPath("$.username").doesNotExist());

        updateProfile(bearer, "  Old.Timer ", " Grace ", " Hopper ")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("old.timer"))
                .andExpect(jsonPath("$.firstName").value("Grace"))
                .andExpect(jsonPath("$.profileComplete").value(true));

        // ...and from now on they can log in with it.
        login("{\"login\":\"old.timer\",\"password\":\"long-enough-1\"}").andExpect(status().isOk());
    }

    @Test
    void profileCanBeChangedButNotToSomeoneElsesUsername() throws Exception {
        register(signUp("taken@example.com", "already.mine")).andExpect(status().isCreated());
        String me = bearerFrom(register(signUp("me@example.com", "me.first")));

        updateProfile(me, "already.mine", "Me", "Too")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.username").value("That username is already taken"));
        // Keeping your own username while changing your name is fine.
        updateProfile(me, "me.first", "New", "Name")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.firstName").value("New"));
        updateProfile(me, "me.second", "New", "Name")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("me.second"));
        updateProfile(me, "x", "", "Name")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.username").exists())
                .andExpect(jsonPath("$.fieldErrors.firstName").value("First name is required"));

        mvc.perform(put("/api/users/me/profile").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"nobody\",\"firstName\":\"A\",\"lastName\":\"B\"}"))
                .andExpect(status().isUnauthorized());
    }

    // ---- live username check ----

    @Test
    void liveCheckSaysWhetherAUsernameIsFree() throws Exception {
        register(signUp("owner@example.com", "popular")).andExpect(status().isCreated());

        check(" Fresh.Name ")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("fresh.name"))
                .andExpect(jsonPath("$.available").value(true))
                .andExpect(jsonPath("$.message").doesNotExist());
        check("Popular")
                .andExpect(jsonPath("$.available").value(false))
                .andExpect(jsonPath("$.message").value("That username is already taken"));
        check("no")
                .andExpect(jsonPath("$.available").value(false))
                .andExpect(jsonPath("$.message").exists());
        check("support").andExpect(jsonPath("$.available").value(false));
    }

    // ---- helpers ----

    private static String signUp(String email, String username) {
        return "{\"email\":\"" + email + "\",\"password\":\"long-enough-1\",\"username\":\"" + username
                + "\",\"firstName\":\"Test\",\"lastName\":\"User\"}";
    }

    private ResultActions register(String json) throws Exception {
        return mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private ResultActions login(String json) throws Exception {
        return mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private ResultActions check(String username) throws Exception {
        return mvc.perform(post("/api/auth/check-username").contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"" + username + "\"}"));
    }

    private ResultActions updateProfile(String bearer, String username, String first, String last) throws Exception {
        return mvc.perform(put("/api/users/me/profile").header("Authorization", bearer)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"" + username + "\",\"firstName\":\"" + first + "\",\"lastName\":\"" + last + "\"}"));
    }

    private static String bearerFrom(ResultActions registered) throws Exception {
        return "Bearer " + JsonPath.read(registered.andReturn().getResponse().getContentAsString(), "$.accessToken");
    }
}
