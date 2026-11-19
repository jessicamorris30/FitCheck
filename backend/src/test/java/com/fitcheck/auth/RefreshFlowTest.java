package com.fitcheck.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * The refresh-cookie lifecycle through real HTTP calls: login sets the cookie, refresh rotates
 * it, a replayed old cookie kills the session, and logout ends it.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class RefreshFlowTest {

    private static final String COOKIE = "fitcheck_refresh";

    @Autowired
    private MockMvc mvc;

    @Test
    void loginSetsSecureRefreshCookie() throws Exception {
        MockHttpServletResponse response = registerAndLogin("cookie@example.com");

        String setCookie = response.getHeader(HttpHeaders.SET_COOKIE);
        assertThat(setCookie)
                .contains(COOKIE + "=")
                .contains("HttpOnly")
                .contains("SameSite=Strict")
                .contains("Path=/api/auth")
                .contains("Max-Age=1209600"); // 14 days in seconds
        // The refresh token must never appear in the JSON body.
        assertThat(response.getContentAsString()).doesNotContain(cookieValue(response));
    }

    @Test
    void refreshReturnsNewAccessTokenAndRotatesCookie() throws Exception {
        String first = cookieValue(registerAndLogin("rotate@example.com"));

        MockHttpServletResponse refreshed = refresh(first)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.user.email").value("rotate@example.com"))
                .andReturn().getResponse();

        assertThat(cookieValue(refreshed)).isNotBlank().isNotEqualTo(first);
    }

    @Test
    void reusingAnOldTokenRevokesTheWholeSession() throws Exception {
        String first = cookieValue(registerAndLogin("reuse@example.com"));
        String second = cookieValue(refresh(first).andExpect(status().isOk()).andReturn().getResponse());

        // An attacker replays the stolen first token → rejected...
        refresh(first).andExpect(status().isUnauthorized());
        // ...and the real user's current token stops working too: the family is revoked.
        refresh(second).andExpect(status().isUnauthorized());
    }

    @Test
    void refreshWithoutCookieIsRejectedAndClearsCookie() throws Exception {
        MockHttpServletResponse response = mvc.perform(post("/api/auth/refresh"))
                .andExpect(status().isUnauthorized())
                .andReturn().getResponse();
        assertThat(response.getHeader(HttpHeaders.SET_COOKIE)).contains("Max-Age=0");
    }

    @Test
    void logoutRevokesSessionAndClearsCookie() throws Exception {
        String token = cookieValue(registerAndLogin("logout@example.com"));

        MockHttpServletResponse response = mvc.perform(post("/api/auth/logout").cookie(new Cookie(COOKIE, token)))
                .andExpect(status().isNoContent())
                .andReturn().getResponse();
        assertThat(response.getHeader(HttpHeaders.SET_COOKIE)).contains("Max-Age=0");

        refresh(token).andExpect(status().isUnauthorized());
    }

    // ---- helpers ----

    private MockHttpServletResponse registerAndLogin(String email) throws Exception {
        String body = "{\"email\":\"" + email + "\",\"password\":\"correct-horse\"}";
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated());
        return mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andReturn().getResponse();
    }

    private org.springframework.test.web.servlet.ResultActions refresh(String token) throws Exception {
        return mvc.perform(post("/api/auth/refresh").cookie(new Cookie(COOKIE, token)));
    }

    private static String cookieValue(MockHttpServletResponse response) {
        Cookie cookie = response.getCookie(COOKIE);
        assertThat(cookie).as("refresh cookie").isNotNull();
        return cookie.getValue();
    }
}
