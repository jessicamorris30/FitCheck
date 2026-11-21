package com.fitcheck.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Base64;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * Starts the app with the PRODUCTION profile, giving it the settings Azure will provide as
 * environment variables (here: pointing at the local docker-compose services). Proves the prod
 * configuration loads and behaves as intended.
 */
@SpringBootTest(properties = {
        "DB_URL=jdbc:postgresql://localhost:5432/fitcheck",
        "POSTGRES_USER=fitcheck",
        "POSTGRES_PASSWORD=fitcheck",
        "JWT_SECRET=" + ProdProfileTest.TEST_JWT_SECRET,
        "AZURE_STORAGE_CONNECTION_STRING=DefaultEndpointsProtocol=http;AccountName=devstoreaccount1;"
                + "AccountKey=Eby8vdM02xNOcqFlqUwJPLlmEtlCDXJ1OUzFT50uSRZ6IFsuFq2UVErCz4I6tq/K1SZFPTOtr/KBHBeksoGMGw==;"
                + "BlobEndpoint=http://127.0.0.1:10000/devstoreaccount1;",
        "MAIL_HOST=localhost",
        "MAIL_PORT=1025",
        "MAIL_USERNAME=test",
        "MAIL_PASSWORD=test",
        "MAIL_FROM=FitCheck <no-reply@example.com>",
        "FRONTEND_URL=https://fitcheck.example.com",
        "CORS_ALLOWED_ORIGINS=https://fitcheck.example.com",
        "fitcheck.rate-limit.enabled=true",
        // Mailpit doesn't speak TLS; real providers do. Only relaxed for this local test.
        "spring.mail.properties.mail.smtp.starttls.required=false",
        "spring.mail.properties.mail.smtp.starttls.enable=false",
        "spring.mail.properties.mail.smtp.auth=false"})
@ActiveProfiles("prod")
@AutoConfigureMockMvc
@Transactional
class ProdProfileTest {

    static final String TEST_JWT_SECRET = "cHJvZC1wcm9maWxlLXRlc3Qtc2VjcmV0LW5vdC1yZWFsLTEyMzQ1Njc4OTA=";

    @Autowired
    private MockMvc mvc;

    @Test
    void refreshCookieRequiresHttpsInProduction() throws Exception {
        MockHttpServletResponse response = mvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"prod-" + System.nanoTime() + "@example.com\",\"password\":\"long-enough-1\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse();
        assertThat(response.getHeader("Set-Cookie")).contains("Secure").contains("HttpOnly").contains("SameSite=Strict");
    }

    @Test
    void rateLimitsUseTheVisitorsRealIpFromAzuresProxy() throws Exception {
        // Two different visitors behind the same proxy (same remote address, different X-Forwarded-For).
        String visitorA = "203.0.113." + (int) (Math.random() * 250);
        for (int i = 0; i < 10; i++) {
            login(visitorA).andExpect(status().isUnauthorized());
        }
        login(visitorA).andExpect(status().isTooManyRequests()); // A is limited...
        login("198.51.100.77").andExpect(status().isUnauthorized()); // ...B is not
    }

    @Test
    void livenessAndReadinessProbesAreAvailable() throws Exception {
        mvc.perform(get("/actuator/health/liveness")).andExpect(status().isOk());
        mvc.perform(get("/actuator/health/readiness")).andExpect(status().isOk());
    }

    @Test
    void testSecretIsValidBase64OfAtLeast32Bytes() {
        assertThat(Base64.getDecoder().decode(TEST_JWT_SECRET).length).isGreaterThanOrEqualTo(32);
    }

    private org.springframework.test.web.servlet.ResultActions login(String forwardedFor) throws Exception {
        return mvc.perform(post("/api/auth/login")
                .header("X-Forwarded-For", forwardedFor)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"nobody-" + System.nanoTime() + "@example.com\",\"password\":\"whatever-1\"}"));
    }
}
