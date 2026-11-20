package com.fitcheck.auth;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

/** POST /api/auth/change-password, including "every other device is signed out". Rolled back. */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ChangePasswordTest {

    private static final String COOKIE = "fitcheck_refresh";

    @Autowired
    private MockMvc mvc;

    /** Registers and logs in on two "devices": returns {accessToken, laptopCookie, phoneCookie}. */
    private String[] userWithTwoDevices(String email) throws Exception {
        String body = "{\"email\":\"" + email + "\",\"password\":\"old-password-1\"}";
        MockHttpServletResponse laptop = mvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON).content(body)).andReturn().getResponse();
        MockHttpServletResponse phone = mvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON).content(body)).andReturn().getResponse();
        String token = JsonPath.read(laptop.getContentAsString(), "$.accessToken");
        return new String[] {token, laptop.getCookie(COOKIE).getValue(), phone.getCookie(COOKIE).getValue()};
    }

    @Test
    void changesThePasswordAndSignsOutEveryOtherDevice() throws Exception {
        String[] s = userWithTwoDevices("change@example.com");

        MockHttpServletResponse response = change(s[0], "old-password-1", "new-password-2")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andReturn().getResponse();
        String newCookie = response.getCookie(COOKIE).getValue();

        // Old password no longer works; the new one does.
        login("change@example.com", "old-password-1").andExpect(status().isUnauthorized());
        login("change@example.com", "new-password-2").andExpect(status().isOk());

        // Both old sessions (laptop and phone) are dead...
        refresh(s[1]).andExpect(status().isUnauthorized());
        refresh(s[2]).andExpect(status().isUnauthorized());
        // ...but the session handed back by the change keeps this device logged in.
        refresh(newCookie).andExpect(status().isOk());
    }

    @Test
    void wrongCurrentPasswordIs400OnThatField() throws Exception {
        String[] s = userWithTwoDevices("wrong@example.com");

        change(s[0], "not-my-password", "new-password-2")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.currentPassword").value("Current password is incorrect"));

        // Nothing changed: the old sessions still work.
        refresh(s[1]).andExpect(status().isOk());
    }

    @Test
    void newPasswordMustBeDifferentAndValid() throws Exception {
        String[] s = userWithTwoDevices("same@example.com");

        change(s[0], "old-password-1", "old-password-1")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.newPassword").value("Choose a password different from your current one"));
        change(s[0], "old-password-1", "short")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.newPassword").value("Password must be 8-72 characters"));
    }

    @Test
    void requiresLoginEvenThoughItLivesUnderApiAuth() throws Exception {
        mvc.perform(post("/api/auth/change-password").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"a\",\"newPassword\":\"bbbbbbbb\"}"))
                .andExpect(status().isUnauthorized());
    }

    // ---- helpers ----

    private ResultActions change(String token, String current, String next) throws Exception {
        return mvc.perform(post("/api/auth/change-password").header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"currentPassword\":\"" + current + "\",\"newPassword\":\"" + next + "\"}"));
    }

    private ResultActions login(String email, String password) throws Exception {
        return mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}"));
    }

    private ResultActions refresh(String cookie) throws Exception {
        return mvc.perform(post("/api/auth/refresh").cookie(new Cookie(COOKIE, cookie)));
    }
}
