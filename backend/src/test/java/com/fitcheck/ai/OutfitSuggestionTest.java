package com.fitcheck.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fitcheck.auth.JwtService;
import com.fitcheck.clothing.ClothingCategory;
import com.fitcheck.clothing.ClothingItem;
import com.fitcheck.clothing.ClothingItemRepository;
import com.fitcheck.storage.BlobStorageService;
import com.fitcheck.storage.TestImages;
import com.fitcheck.stylist.AiRefusedException;
import com.fitcheck.stylist.FakeStylistClient;
import com.fitcheck.stylist.StylistClient;
import com.fitcheck.stylist.StylistModels.Suggestion;
import com.fitcheck.stylist.StylistModels.SuggestionResult;
import com.fitcheck.stylist.StylistModels.WardrobeEntry;
import com.fitcheck.user.User;
import com.fitcheck.user.UserRepository;
import java.util.ArrayList;
import java.util.List;
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
 * POST /api/ai/outfit-suggestions with a FAKE Claude (see FakeStylistClient): checks what we
 * send and how we handle the answer, without any real API calls. Rolled back after each test.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class OutfitSuggestionTest {

    /** Replace the real StylistClient with the fake for this test class. */
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
    private BlobStorageService storage;

    @Autowired
    private JwtService jwt;

    @Autowired
    private StylistClient stylist;

    private FakeStylistClient fake;
    private User alice;
    private ClothingItem tee;
    private ClothingItem jeans;
    private ClothingItem boots;
    private ClothingItem bobsHat;
    private final List<String> blobsToDelete = new ArrayList<>();

    @BeforeEach
    void setUp() {
        fake = (FakeStylistClient) stylist;
        // The fake is ONE shared object for the whole test class: reset what earlier tests left behind.
        fake.lastRequest = null;
        fake.answer = req -> new SuggestionResult(List.of());
        User newAlice = new User("alice-" + UUID.randomUUID() + "@example.com", "hash");
        newAlice.markEmailVerified(java.time.Instant.now()); // the AI stylist requires a confirmed email (step 4.5)
        alice = users.saveAndFlush(newAlice);
        User bob = users.saveAndFlush(new User("bob-" + UUID.randomUUID() + "@example.com", "hash"));
        tee = item(alice, "White tee", ClothingCategory.TOP, true);
        jeans = item(alice, "Blue jeans", ClothingCategory.BOTTOM, true);
        boots = item(alice, "Leather boots", ClothingCategory.SHOES, false); // no thumbnail yet
        bobsHat = item(bob, "Bob's hat", ClothingCategory.ACCESSORY, false);
    }

    @AfterEach
    void cleanUp() {
        blobsToDelete.forEach(storage::delete);
    }

    private ClothingItem item(User owner, String name, ClothingCategory category, boolean withThumbnail) {
        String base = "users/" + owner.getId() + "/" + UUID.randomUUID();
        ClothingItem item = new ClothingItem(owner, name, category, "blue", List.of("casual"), base + ".jpg");
        if (withThumbnail) {
            storage.upload(base + "_thumb.jpg", TestImages.jpeg(32, 40), "image/jpeg");
            item.setThumbnailBlobName(base + "_thumb.jpg");
            blobsToDelete.add(base + "_thumb.jpg");
        }
        return clothing.saveAndFlush(item);
    }

    @Test
    void sendsTheWholeWardrobeOldestFirstWithPhotosAndThePrompt() throws Exception {
        fake.answer = req -> new SuggestionResult(List.of(
                new Suggestion("Casual Friday", List.of(jeans.getId(), tee.getId()), "Relaxed but neat.")));

        ask(alice, "casual friday at the office")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.suggestions", hasSize(1)))
                .andExpect(jsonPath("$.suggestions[0].title").value("Casual Friday"))
                .andExpect(jsonPath("$.suggestions[0].reasoning").value("Relaxed but neat."))
                .andExpect(jsonPath("$.suggestions[0].items[*].name").value(contains("Blue jeans", "White tee")))
                .andExpect(jsonPath("$.suggestions[0].items[0].thumbnailUrl").isNotEmpty());

        assertThat(fake.lastRequest.prompt()).isEqualTo("casual friday at the office");
        // Only Alice's clothes, oldest first (stable order for prompt caching), never Bob's hat.
        assertThat(fake.lastRequest.wardrobe()).extracting(WardrobeEntry::name)
                .containsExactly("White tee", "Blue jeans", "Leather boots");
        // Photos are included where a thumbnail exists.
        assertThat(fake.lastRequest.wardrobe().get(0).thumbnailJpeg()).isNotEmpty();
        assertThat(fake.lastRequest.wardrobe().get(2).thumbnailJpeg()).isNull();
    }

    @Test
    void dropsIdsThatArentTheUsersAndSuggestionsLeftEmpty() throws Exception {
        fake.answer = req -> new SuggestionResult(List.of(
                new Suggestion("Mixed", List.of(tee.getId(), bobsHat.getId(), UUID.randomUUID()), "..."),
                new Suggestion("Nothing valid", List.of(bobsHat.getId()), "...")));

        ask(alice, "anything")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.suggestions", hasSize(1)))
                .andExpect(jsonPath("$.suggestions[0].items[*].name").value(contains("White tee")));
    }

    @Test
    void noUsableSuggestionsIs503() throws Exception {
        fake.answer = req -> new SuggestionResult(List.of(new Suggestion("Empty", List.of(), "...")));
        ask(alice, "anything").andExpect(status().isServiceUnavailable());
    }

    @Test
    void refusalIs422WithAFriendlyMessage() throws Exception {
        fake.answer = req -> {
            throw new AiRefusedException("declined");
        };
        ask(alice, "something odd")
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.message").value("The stylist couldn't help with that request. Try describing it differently."));
    }

    @Test
    void emptyWardrobeIs400() throws Exception {
        User carol = users.saveAndFlush(new User("carol-" + UUID.randomUUID() + "@example.com", "hash"));
        ask(carol, "anything")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Add some clothes to your wardrobe first, then ask the stylist."));
    }

    @Test
    void validatesThePromptAndRequiresLogin() throws Exception {
        ask(alice, "").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.prompt").value("Describe the look you're going for"));
        mvc.perform(post("/api/ai/outfit-suggestions").contentType(MediaType.APPLICATION_JSON).content("{\"prompt\":\"x\"}"))
                .andExpect(status().isUnauthorized());
    }

    // ---- inspiration photo (step 4.4) ----

    @Test
    void photoOnlyIsShrunkAndSentWithoutAPrompt() throws Exception {
        fake.answer = req -> new SuggestionResult(List.of(new Suggestion("Like the photo", List.of(tee.getId()), "...")));

        askWithPhoto(alice, null, TestImages.jpeg(2000, 1500)).andExpect(status().isOk())
                .andExpect(jsonPath("$.suggestions[0].title").value("Like the photo"));

        assertThat(fake.lastRequest.prompt()).isNull();
        var sent = TestImages.decode(fake.lastRequest.inspirationJpeg());
        assertThat(sent.getWidth()).isEqualTo(1024); // 2000×1500 → at most 1024 on the long side
        assertThat(sent.getHeight()).isEqualTo(768);
    }

    @Test
    void photoAndTextAreSentTogether() throws Exception {
        fake.answer = req -> new SuggestionResult(List.of(new Suggestion("Both", List.of(jeans.getId()), "...")));

        askWithPhoto(alice, "but warmer", TestImages.png(300, 400)).andExpect(status().isOk());

        assertThat(fake.lastRequest.prompt()).isEqualTo("but warmer");
        assertThat(fake.lastRequest.inspirationJpeg()).isNotEmpty();
    }

    @Test
    void needsAPromptOrAPhoto() throws Exception {
        askWithPhoto(alice, "  ", null)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Describe the look you're going for, add an inspiration photo, or both."));
    }

    @Test
    void rejectsAPhotoThatIsntAnImage() throws Exception {
        askWithPhoto(alice, null, "definitely not a picture".getBytes())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("The inspiration photo must be a JPEG, PNG or WebP image."));
        assertThat(fake.lastRequest).isNull(); // Claude was never called
    }

    @Test
    void rejectsATooLongPromptWithAPhoto() throws Exception {
        askWithPhoto(alice, "x".repeat(501), TestImages.png(10, 10)).andExpect(status().isBadRequest());
    }

    private ResultActions askWithPhoto(User user, String prompt, byte[] photo) throws Exception {
        var request = multipart("/api/ai/outfit-suggestions")
                .header("Authorization", "Bearer " + jwt.createAccessToken(user.getId(), user.getEmail()));
        if (photo != null) {
            request.file(new MockMultipartFile("image", "inspo.jpg", "image/jpeg", photo));
        }
        if (prompt != null) {
            request.param("prompt", prompt);
        }
        return mvc.perform(request);
    }

    private ResultActions ask(User user, String prompt) throws Exception {
        return mvc.perform(post("/api/ai/outfit-suggestions")
                .header("Authorization", "Bearer " + jwt.createAccessToken(user.getId(), user.getEmail()))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"prompt\":\"" + prompt + "\"}"));
    }
}
