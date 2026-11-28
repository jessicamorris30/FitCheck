package com.fitcheck.style;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fitcheck.auth.JwtService;
import com.fitcheck.stylist.FakeStylistClient;
import com.fitcheck.stylist.StylistClient;
import com.fitcheck.user.User;
import com.fitcheck.user.UserRepository;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/**
 * The real background path: saving a change really triggers the notes being written AFTER the
 * save commits, on another thread, and quick changes in a row cost one Claude call.
 *
 * <p>Deliberately NOT @Transactional: the trigger only fires when a transaction commits, and a
 * test transaction is always rolled back. So this test cleans up after itself instead.
 */
@SpringBootTest(properties = "fitcheck.style.describe-delay=1500ms")
@AutoConfigureMockMvc
class StyleBackgroundTest {

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
    private UserStyleRepository styles;

    @Autowired
    private JwtService jwt;

    @Autowired
    private StylistClient stylist;

    private User user;

    @AfterEach
    void cleanUp() {
        if (user != null) {
            users.deleteById(user.getId()); // the style row goes with it (ON DELETE CASCADE)
        }
    }

    @Test
    void threeQuickChangesAreDescribedOnceAfterTheyAreSaved() throws Exception {
        FakeStylistClient fake = (FakeStylistClient) stylist;
        fake.describeCalls = 0;
        fake.styleAnswer = sample -> "Notes for: " + sample.note();
        User u = new User("bg-" + UUID.randomUUID() + "@example.com", "hash");
        u.markEmailVerified(Instant.now());
        user = users.saveAndFlush(u);
        String bearer = "Bearer " + jwt.createAccessToken(user.getId(), user.getEmail());

        for (String note : new String[] {"Black", "Black and white", "Black, white and grey"}) {
            mvc.perform(put("/api/users/me/style").header("Authorization", bearer)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"note\":\"" + note + "\",\"useWithAi\":true}"))
                    .andExpect(status().isOk());
        }

        // Written in the background (each run waits 1.5 s, then only the newest one goes ahead).
        long deadline = System.currentTimeMillis() + 15_000;
        String notes = null;
        while (System.currentTimeMillis() < deadline) {
            notes = styles.findById(user.getId()).map(UserStyle::getAiDescription).orElse(null);
            if (notes != null) {
                break;
            }
            Thread.sleep(200);
        }
        Thread.sleep(2_000); // give any (wrong) extra runs time to show up
        assertThat(notes).isEqualTo("Notes for: Black, white and grey");
        assertThat(fake.describeCalls).isEqualTo(1);
    }
}
