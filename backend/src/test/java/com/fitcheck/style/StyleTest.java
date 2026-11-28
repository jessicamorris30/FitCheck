package com.fitcheck.style;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fitcheck.auth.JwtService;
import com.fitcheck.storage.BlobStorageService;
import com.fitcheck.storage.TestImages;
import com.fitcheck.style.StyleDescriber.Outcome;
import com.fitcheck.stylist.AiRefusedException;
import com.fitcheck.stylist.AiUnavailableException;
import com.fitcheck.stylist.FakeStylistClient;
import com.fitcheck.stylist.StylistClient;
import com.fitcheck.user.User;
import com.fitcheck.user.UserRepository;
import com.jayway.jsonpath.JsonPath;
import java.awt.image.BufferedImage;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

/**
 * "My style" (step 7.3): the user's note, switch and photos through the API, and the background
 * writing of Claude's PRIVATE style notes (with a fake Claude).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class StyleTest {

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
    private StyleDescriber describer;

    @Autowired
    private BlobStorageService storage;

    @Autowired
    private JwtService jwt;

    @Autowired
    private StylistClient stylist;

    private FakeStylistClient fake;
    private User alice;
    private User bob;

    @BeforeEach
    void setUp() {
        fake = (FakeStylistClient) stylist;
        fake.styleAnswer = sample -> "Fake style notes: " + sample.photosJpeg().size() + " photo(s), note: " + sample.note();
        fake.describeCalls = 0;
        fake.lastStyleSample = null;
        User a = new User("alice-" + UUID.randomUUID() + "@example.com", "hash");
        a.markEmailVerified(Instant.now()); // like the stylist, style notes need a confirmed email
        alice = users.saveAndFlush(a);
        bob = users.saveAndFlush(new User("bob-" + UUID.randomUUID() + "@example.com", "hash"));
    }

    @AfterEach
    void removeFiles() {
        // The database is rolled back after each test; the uploaded files in storage are not.
        storage.deleteAllWithPrefix("users/" + alice.getId() + "/");
        storage.deleteAllWithPrefix("users/" + bob.getId() + "/");
    }

    // ---- the API the page uses ----

    @Test
    void aNewUserHasAnEmptyStyleWithTheSwitchOn() throws Exception {
        getStyle(alice)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.note").doesNotExist())
                .andExpect(jsonPath("$.useWithAi").value(true))
                .andExpect(jsonPath("$.photos", hasSize(0)))
                .andExpect(jsonPath("$.maxPhotos").value(5));
    }

    @Test
    void saveNoteAndSwitch() throws Exception {
        putStyle(alice, "{\"note\":\"  Mostly neutrals, wide-leg trousers, no heels \",\"useWithAi\":false}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.note").value("Mostly neutrals, wide-leg trousers, no heels"))
                .andExpect(jsonPath("$.useWithAi").value(false));
        getStyle(alice).andExpect(jsonPath("$.note").value("Mostly neutrals, wide-leg trousers, no heels"));

        putStyle(alice, "{\"note\":\"" + "x".repeat(1001) + "\",\"useWithAi\":true}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.note").value("Keep it under 1000 characters"));
    }

    @Test
    void onlyAChangedNoteMakesTheNotesOutOfDate() throws Exception {
        putStyle(alice, "{\"note\":\"Minimal\",\"useWithAi\":true}");
        int version = styles.findById(alice.getId()).orElseThrow().getVersion();

        putStyle(alice, "{\"note\":\"Minimal\",\"useWithAi\":false}"); // just the switch
        assertThat(styles.findById(alice.getId()).orElseThrow().getVersion()).isEqualTo(version);

        putStyle(alice, "{\"note\":\"Minimal, but colourful\",\"useWithAi\":false}");
        assertThat(styles.findById(alice.getId()).orElseThrow().getVersion()).isEqualTo(version + 1);
    }

    @Test
    void photosAreStoredSmallAsJpegWithSignedLinks() throws Exception {
        String body = addPhoto(alice, TestImages.png(2400, 1600))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.photos", hasSize(1)))
                .andExpect(jsonPath("$.photos[0].id").isNotEmpty())
                .andExpect(jsonPath("$.photos[0].imageUrl", containsString("sig=")))
                .andExpect(jsonPath("$.photos[0].thumbnailUrl", containsString("sig=")))
                .andReturn().getResponse().getContentAsString();

        // The link encodes "/" in the file path as %2F; decode it to read the file name back.
        String imageUrl = java.net.URLDecoder.decode(JsonPath.read(body, "$.photos[0].imageUrl"),
                java.nio.charset.StandardCharsets.UTF_8);
        String blob = imageUrl.substring(imageUrl.indexOf("users/"), imageUrl.indexOf('?'));
        assertThat(blob).startsWith("users/" + alice.getId() + "/style/").endsWith(".jpg");
        BufferedImage stored = TestImages.decode(storage.download(blob));
        assertThat(stored.getWidth()).isEqualTo(1024); // re-saved at 1024 px (no huge originals, no metadata)
        assertThat(stored.getHeight()).isEqualTo(683);
    }

    @Test
    void atMostFivePhotosAndOnlyImages() throws Exception {
        for (int i = 0; i < 5; i++) {
            addPhoto(alice, TestImages.jpeg(400, 300)).andExpect(status().isCreated());
        }
        addPhoto(alice, TestImages.jpeg(400, 300))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("You can add up to 5 style photos. Remove one first."));

        mvc.perform(multipart("/api/users/me/style/photos")
                        .file(new MockMultipartFile("image", "x.jpg", "image/jpeg", "not really an image".getBytes()))
                        .header("Authorization", bearer(bob)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Only JPEG, PNG or WebP images are allowed"));
    }

    @Test
    void removeAPhotoButNotSomeoneElses() throws Exception {
        String body = addPhoto(alice, TestImages.jpeg(400, 300)).andReturn().getResponse().getContentAsString();
        String photoId = JsonPath.read(body, "$.photos[0].id");

        mvc.perform(delete("/api/users/me/style/photos/" + photoId).header("Authorization", bearer(bob)))
                .andExpect(status().isNotFound());
        mvc.perform(delete("/api/users/me/style/photos/" + photoId).header("Authorization", bearer(alice)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.photos", hasSize(0)));
    }

    @Test
    void needsLogin() throws Exception {
        mvc.perform(get("/api/users/me/style")).andExpect(status().isUnauthorized());
    }

    // ---- Claude's private notes ----

    @Test
    void notesAreWrittenFromPhotosAndNoteButNeverSentToTheBrowser() throws Exception {
        addPhoto(alice, TestImages.jpeg(400, 300));
        addPhoto(alice, TestImages.jpeg(400, 300));
        putStyle(alice, "{\"note\":\"Clean lines, lots of black\",\"useWithAi\":true}");

        assertThat(describer.describeIfStale(alice.getId())).isEqualTo(Outcome.WRITTEN);
        assertThat(fake.describeCalls).isEqualTo(1);
        assertThat(fake.lastStyleSample.photosJpeg()).hasSize(2);
        assertThat(fake.lastStyleSample.note()).isEqualTo("Clean lines, lots of black");
        String notes = styles.findById(alice.getId()).orElseThrow().getAiDescription();
        assertThat(notes).isEqualTo("Fake style notes: 2 photo(s), note: Clean lines, lots of black");

        // Not anywhere in what the browser can fetch.
        String json = getStyle(alice).andReturn().getResponse().getContentAsString();
        assertThat(json).doesNotContain("Fake style notes").doesNotContain("aiDescription").doesNotContain("description");

        // Nothing changed since: no second Claude call.
        assertThat(describer.describeIfStale(alice.getId())).isEqualTo(Outcome.UP_TO_DATE);
        assertThat(fake.describeCalls).isEqualTo(1);
    }

    @Test
    void aRunForAnOlderVersionStepsAsideForTheNewerOne() throws Exception {
        putStyle(alice, "{\"note\":\"First idea\",\"useWithAi\":true}");
        int older = styles.findById(alice.getId()).orElseThrow().getVersion();
        putStyle(alice, "{\"note\":\"Second idea\",\"useWithAi\":true}");

        // Like the run for the first change waking up after the second one: it does nothing...
        assertThat(describer.describe(alice.getId(), older)).isEqualTo(Outcome.SUPERSEDED);
        assertThat(fake.describeCalls).isZero();
        // ...and the run for the newest version describes the newest note.
        assertThat(describer.describe(alice.getId(), older + 1)).isEqualTo(Outcome.WRITTEN);
        assertThat(fake.lastStyleSample.note()).isEqualTo("Second idea");
    }

    @Test
    void unconfirmedEmailsGetNoNotesYet() throws Exception {
        putStyle(bob, "{\"note\":\"Streetwear\",\"useWithAi\":true}");

        assertThat(describer.describeIfStale(bob.getId())).isEqualTo(Outcome.SKIPPED_UNCONFIRMED);
        assertThat(fake.describeCalls).isZero();
    }

    @Test
    void atMostTenNotesPerDay() throws Exception {
        for (int i = 1; i <= StyleDescriber.DAILY_LIMIT; i++) {
            putStyle(alice, "{\"note\":\"Version " + i + "\",\"useWithAi\":true}");
            assertThat(describer.describeIfStale(alice.getId())).isEqualTo(Outcome.WRITTEN);
        }
        putStyle(alice, "{\"note\":\"One change too many\",\"useWithAi\":true}");

        assertThat(describer.describeIfStale(alice.getId())).isEqualTo(Outcome.SKIPPED_DAILY_LIMIT);
        assertThat(fake.describeCalls).isEqualTo(StyleDescriber.DAILY_LIMIT);
    }

    @Test
    void removingEverythingClearsTheNotesWithoutAskingClaude() throws Exception {
        putStyle(alice, "{\"note\":\"Boho\",\"useWithAi\":true}");
        describer.describeIfStale(alice.getId());
        putStyle(alice, "{\"note\":\"\",\"useWithAi\":true}");

        assertThat(describer.describeIfStale(alice.getId())).isEqualTo(Outcome.CLEARED);
        assertThat(styles.findById(alice.getId()).orElseThrow().getAiDescription()).isNull();
        assertThat(fake.describeCalls).isEqualTo(1);
    }

    @Test
    void whenClaudeDeclinesTheNotesStayEmptyAndItIsNotRetried() throws Exception {
        fake.styleAnswer = sample -> {
            throw new AiRefusedException("declined");
        };
        putStyle(alice, "{\"note\":\"Something odd\",\"useWithAi\":true}");

        assertThat(describer.describeIfStale(alice.getId())).isEqualTo(Outcome.WRITTEN);
        assertThat(styles.findById(alice.getId()).orElseThrow().getAiDescription()).isNull();
        assertThat(describer.describeIfStale(alice.getId())).isEqualTo(Outcome.UP_TO_DATE);
    }

    @Test
    void whenClaudeIsDownTheNotesStayOutOfDateForALaterRetry() throws Exception {
        fake.styleAnswer = sample -> {
            throw new AiUnavailableException("down");
        };
        putStyle(alice, "{\"note\":\"Preppy\",\"useWithAi\":true}");

        assertThat(describer.describeIfStale(alice.getId())).isEqualTo(Outcome.FAILED);
        assertThat(styles.findById(alice.getId()).orElseThrow().isDescriptionStale()).isTrue();

        fake.styleAnswer = sample -> "Preppy notes";
        assertThat(describer.describeIfStale(alice.getId())).isEqualTo(Outcome.WRITTEN);
    }

    // ---- helpers ----

    private ResultActions getStyle(User user) throws Exception {
        return mvc.perform(get("/api/users/me/style").header("Authorization", bearer(user)));
    }

    private ResultActions putStyle(User user, String json) throws Exception {
        return mvc.perform(put("/api/users/me/style").header("Authorization", bearer(user))
                .contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private ResultActions addPhoto(User user, byte[] image) throws Exception {
        return mvc.perform(multipart("/api/users/me/style/photos")
                .file(new MockMultipartFile("image", "style.png", "image/png", image))
                .header("Authorization", bearer(user)));
    }

    private String bearer(User user) {
        return "Bearer " + jwt.createAccessToken(user.getId(), user.getEmail());
    }
}
