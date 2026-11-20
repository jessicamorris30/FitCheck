package com.fitcheck.stylist;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fitcheck.auth.JwtService;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

/** The status endpoint and the "no API key" behaviour. Neither calls Claude. */
@SpringBootTest
@AutoConfigureMockMvc
class AiStatusTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JwtService jwt;

    @Test
    void statusShowsTheModelAndRequiresLogin() throws Exception {
        mvc.perform(get("/api/ai/status")).andExpect(status().isUnauthorized());

        String token = jwt.createAccessToken(UUID.randomUUID(), "someone@example.com");
        mvc.perform(get("/api/ai/status").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.model").value("claude-opus-5-5"))
                .andExpect(jsonPath("$.configured").isBoolean());
    }

    @Test
    void withoutAKeyAiRequestsFailCleanly() {
        StylistClient off = new NotConfiguredStylistClient("claude-opus-5-5");

        assertThat(off.isConfigured()).isFalse();
        assertThatThrownBy(off::checkConnection)
                .isInstanceOf(AiUnavailableException.class)
                .hasMessageContaining("isn't configured");
    }
}
