package com.fitcheck.config;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.forwardedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

/**
 * The backend serving the React app at the same address as the API (step 5.6). Uses the stand-in
 * files in src/test/resources/static/ instead of a real frontend build.
 */
@SpringBootTest
@AutoConfigureMockMvc
class SpaHostingTest {

    @Autowired
    private MockMvc mvc;

    @Test
    void homePageIsTheApp() throws Exception {
        // For "/", Spring Boot's built-in "welcome page" support FORWARDS internally to index.html.
        // A real server follows that forward; MockMvc only records it, so we check the forward here.
        // (The Docker image in step 5.7 proves "/" really shows the app.)
        mvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(forwardedUrl("index.html"));
    }

    @Test
    void appPagesFallBackToIndexHtmlAndArentCached() throws Exception {
        for (String page : new String[] {"/wardrobe", "/wardrobe/123e4567-e89b-12d3-a456-426614174000", "/plan", "/login"}) {
            mvc.perform(get(page))
                    .andExpect(status().isOk())
                    .andExpect(content().string(containsString("FitCheck test index")))
                    .andExpect(header().string("Cache-Control", containsString("no-cache")));
        }
    }

    @Test
    void builtAssetsAreServedWithALongCache() throws Exception {
        mvc.perform(get("/assets/app-test123.js"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("fitcheck test asset")))
                .andExpect(header().string("Cache-Control", containsString("max-age=31536000")))
                .andExpect(header().string("Cache-Control", containsString("immutable")));
    }

    @Test
    void missingFilesAreReal404sNotTheApp() throws Exception {
        mvc.perform(get("/assets/missing.js")).andExpect(status().isNotFound());
        mvc.perform(get("/robots-not-here.txt")).andExpect(status().isNotFound());
    }

    @Test
    void unknownApiPathsStayJson404sAndTheApiStaysProtected() throws Exception {
        mvc.perform(get("/api/does-not-exist").header("Authorization", "Bearer x"))
                .andExpect(content().string(not(containsString("FitCheck test index"))));
        mvc.perform(get("/api/clothing"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Authentication required"));
    }

    @Test
    void healthProbesAreOpenOtherActuatorEndpointsAreNot() throws Exception {
        mvc.perform(get("/actuator/health")).andExpect(status().isOk());
        mvc.perform(get("/actuator/info")).andExpect(status().isUnauthorized());
        mvc.perform(get("/actuator/env")).andExpect(status().isUnauthorized());
    }
}
