package com.fitcheck.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Base64;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
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
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
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

    private static final HttpClient http = HttpClient.newHttpClient();

    @Autowired
    private MockMvc mvc;

    @LocalServerPort
    private int port;

    @Test
    void refreshCookieRequiresHttpsInProduction() throws Exception {
        MockHttpServletResponse response = mvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"prod-" + System.nanoTime() + "@example.com\",\"password\":\"long-enough-1\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse();
        assertThat(response.getHeader("Set-Cookie")).contains("Secure").contains("HttpOnly").contains("SameSite=Strict");
    }

    /**
     * Real HTTP requests to the running server (not MockMvc), because X-Forwarded-For is handled
     * inside the web server itself. The test connects from 127.0.0.1, a trusted internal address,
     * just like Azure's front door does.
     */
    @Test
    void rateLimitsUseTheIpAzureRecorded_notOneTheVisitorMadeUp() throws Exception {
        // Azure APPENDS the visitor's real address; everything before it came from the visitor.
        String realIp = "203.0.113." + (1 + (int) (Math.random() * 120));
        for (int i = 0; i < 10; i++) {
            // A different made-up first entry every time, like an attacker would send.
            assertThat(httpLogin("198.51.100." + i + ", " + realIp)).isEqualTo(401);
        }
        assertThat(httpLogin("198.51.100.99, " + realIp)).isEqualTo(429); // still limited
        // A genuinely different visitor isn't affected.
        assertThat(httpLogin("203.0.113." + (130 + (int) (Math.random() * 120)))).isEqualTo(401);
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

    private int httpLogin(String forwardedFor) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/api/auth/login"))
                .header("X-Forwarded-For", forwardedFor)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(
                        "{\"email\":\"nobody-" + System.nanoTime() + "@example.com\",\"password\":\"whatever-1\"}"))
                .build();
        return http.send(request, HttpResponse.BodyHandlers.discarding()).statusCode();
    }
}
