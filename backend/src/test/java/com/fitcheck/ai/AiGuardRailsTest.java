package com.fitcheck.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fitcheck.auth.JwtService;
import com.fitcheck.clothing.ClothingCategory;
import com.fitcheck.clothing.ClothingItem;
import com.fitcheck.clothing.ClothingItemRepository;
import com.fitcheck.stylist.AiRefusedException;
import com.fitcheck.stylist.AiUnavailableException;
import com.fitcheck.stylist.FakeStylistClient;
import com.fitcheck.stylist.StylistClient;
import com.fitcheck.stylist.StylistModels.Suggestion;
import com.fitcheck.stylist.StylistModels.SuggestionResult;
import com.fitcheck.user.User;
import com.fitcheck.user.UserRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

/** Confirmed-email rule, daily limit, refunds and the usage endpoint. Fake Claude; rolled back. */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AiGuardRailsTest {

    @TestConfiguration
    static class FakeClaude {
        @Bean
        @Primary
        StylistClient fakeStylistClient() {
            return new FakeStylistClient();
        }
    }

    @Autowired
    private MockMvc mvc;

    @Autowired
    private UserRepository users;

    @Autowired
    private ClothingItemRepository clothing;

    @Autowired
    private AiUsageRepository usage;

    @Autowired
    private JwtService jwt;

    @Autowired
    private StylistClient stylist;

    private FakeStylistClient fake;
    private User verified;
    private ClothingItem tee;

    @BeforeEach
    void setUp() {
        fake = (FakeStylistClient) stylist;
        fake.lastRequest = null;
        User u = new User("ai-" + UUID.randomUUID() + "@example.com", "hash");
        u.markEmailVerified(Instant.now());
        verified = users.saveAndFlush(u);
        tee = clothing.saveAndFlush(new ClothingItem(verified, "White tee", ClothingCategory.TOP, null, List.of(),
                "users/" + verified.getId() + "/" + UUID.randomUUID() + ".jpg"));
        fake.answer = req -> new SuggestionResult(List.of(new Suggestion("Easy", List.of(tee.getId()), "...")));
    }

    @Test
    void unconfirmedEmailIsBlockedBeforeClaudeIsCalled() throws Exception {
        User unconfirmed = users.saveAndFlush(new User("new-" + UUID.randomUUID() + "@example.com", "hash"));
        clothing.saveAndFlush(new ClothingItem(unconfirmed, "Hat", ClothingCategory.ACCESSORY, null, List.of(),
                "users/" + unconfirmed.getId() + "/" + UUID.randomUUID() + ".jpg"));

        ask(unconfirmed, "anything")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Please confirm your email to use the AI stylist."));
        assertThat(fake.lastRequest).isNull();
    }

    @Test
    void eachSuccessfulRequestCountsAndTheLimitIs20() throws Exception {
        ask(verified, "look one").andExpect(status().isOk());
        usage(verified).andExpect(jsonPath("$.used").value(1)).andExpect(jsonPath("$.remaining").value(19));

        // Pretend 19 more requests were made today (cheaper than calling the endpoint 19 times).
        for (int i = 0; i < 19; i++) {
            usage.save(new AiUsage(verified, Instant.now()));
        }
        fake.lastRequest = null;

        ask(verified, "look twenty-one")
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.message").value("You've used all 20 AI suggestions for today. Try again tomorrow."));
        assertThat(fake.lastRequest).isNull();
        usage(verified).andExpect(jsonPath("$.used").value(20)).andExpect(jsonPath("$.remaining").value(0));
    }

    @Test
    void requestsFromBeforeTodayDontCount() throws Exception {
        Instant yesterday = LocalDate.now(ZoneOffset.UTC).atStartOfDay(ZoneOffset.UTC).toInstant().minusSeconds(1);
        for (int i = 0; i < 20; i++) {
            usage.save(new AiUsage(verified, yesterday));
        }
        ask(verified, "fresh day").andExpect(status().isOk());
    }

    @Test
    void outageIsRefundedButRefusalCounts() throws Exception {
        fake.answer = req -> {
            throw new AiUnavailableException("API down");
        };
        ask(verified, "anything").andExpect(status().isServiceUnavailable());
        usage(verified).andExpect(jsonPath("$.used").value(0));

        fake.answer = req -> {
            throw new AiRefusedException("declined");
        };
        ask(verified, "anything").andExpect(status().isUnprocessableContent());
        usage(verified).andExpect(jsonPath("$.used").value(1));
    }

    @Test
    void invalidRequestsDontUseUpTheLimit() throws Exception {
        ask(verified, "").andExpect(status().isBadRequest());
        usage(verified).andExpect(jsonPath("$.used").value(0));
    }

    @Test
    void usageEndpointShowsLimitAndResetTime() throws Exception {
        usage(verified)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.used").value(0))
                .andExpect(jsonPath("$.limit").value(20))
                .andExpect(jsonPath("$.remaining").value(20))
                .andExpect(jsonPath("$.resetsAt").value(
                        LocalDate.now(ZoneOffset.UTC).plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant().toString()));
    }

    private ResultActions ask(User user, String prompt) throws Exception {
        return mvc.perform(post("/api/ai/outfit-suggestions")
                .header("Authorization", "Bearer " + jwt.createAccessToken(user.getId(), user.getEmail()))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"prompt\":\"" + prompt + "\"}"));
    }

    private ResultActions usage(User user) throws Exception {
        return mvc.perform(get("/api/ai/usage")
                .header("Authorization", "Bearer " + jwt.createAccessToken(user.getId(), user.getEmail())));
    }
}
