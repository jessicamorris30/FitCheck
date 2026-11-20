package com.fitcheck.outfit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fitcheck.auth.JwtService;
import com.fitcheck.clothing.ClothingCategory;
import com.fitcheck.clothing.ClothingItem;
import com.fitcheck.clothing.ClothingItemRepository;
import com.fitcheck.user.User;
import com.fitcheck.user.UserRepository;
import com.jayway.jsonpath.JsonPath;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

/** PUT / DELETE /api/outfits/{id}. Rolled back after each test. */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class OutfitEditTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private UserRepository users;

    @Autowired
    private ClothingItemRepository clothing;

    @Autowired
    private OutfitRepository outfits;

    @Autowired
    private JwtService jwt;

    private User alice;
    private User bob;
    private ClothingItem tee;
    private ClothingItem jeans;
    private ClothingItem sneakers;
    private ClothingItem jacket;
    private ClothingItem bobsHat;
    private Outfit outfit;

    @BeforeEach
    void setUp() {
        alice = users.saveAndFlush(new User("alice-" + UUID.randomUUID() + "@example.com", "hash"));
        bob = users.saveAndFlush(new User("bob-" + UUID.randomUUID() + "@example.com", "hash"));
        tee = item(alice, "White tee", ClothingCategory.TOP);
        jeans = item(alice, "Blue jeans", ClothingCategory.BOTTOM);
        sneakers = item(alice, "Sneakers", ClothingCategory.SHOES);
        jacket = item(alice, "Denim jacket", ClothingCategory.OUTERWEAR);
        bobsHat = item(bob, "Bob's hat", ClothingCategory.ACCESSORY);

        outfit = new Outfit(alice, "Weekend", null);
        outfit.replaceItems(List.of(tee, jeans, sneakers));
        outfits.saveAndFlush(outfit);
    }

    private ClothingItem item(User owner, String name, ClothingCategory category) {
        return clothing.saveAndFlush(new ClothingItem(owner, name, category, null, List.of(),
                "users/" + owner.getId() + "/" + UUID.randomUUID() + ".jpg"));
    }

    @Test
    void updateRenamesAndReordersRemovesAndAddsItems() throws Exception {
        Instant before = outfit.getUpdatedAt();

        String body = update(alice, outfit.getId(), "Weekend v2", "cooler day", jeans, tee, jacket)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Weekend v2"))
                .andExpect(jsonPath("$.notes").value("cooler day"))
                .andExpect(jsonPath("$.items[*].name").value(contains("Blue jeans", "White tee", "Denim jacket")))
                .andReturn().getResponse().getContentAsString();

        // Only the items changed in a way that touches outfit_items, but the outfit still counts as changed.
        Instant after = Instant.parse(JsonPath.read(body, "$.updatedAt"));
        assertThat(after).isAfter(before);

        mvc.perform(get("/api/outfits/" + outfit.getId()).header("Authorization", bearer(alice)))
                .andExpect(jsonPath("$.items[*].name").value(contains("Blue jeans", "White tee", "Denim jacket")));
    }

    @Test
    void updateRejectsSomeoneElsesItemAndChangesNothing() throws Exception {
        update(alice, outfit.getId(), "Sneaky", null, tee, bobsHat).andExpect(status().isBadRequest());

        mvc.perform(get("/api/outfits/" + outfit.getId()).header("Authorization", bearer(alice)))
                .andExpect(jsonPath("$.name").value("Weekend"))
                .andExpect(jsonPath("$.items[*].name").value(contains("White tee", "Blue jeans", "Sneakers")));
    }

    @Test
    void updateValidatesTheBody() throws Exception {
        mvc.perform(put("/api/outfits/" + outfit.getId()).header("Authorization", bearer(alice))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"\",\"itemIds\":[]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.itemIds").value("Add at least one item"));
    }

    @Test
    void cannotEditOrDeleteSomeoneElsesOutfit() throws Exception {
        update(bob, outfit.getId(), "Mine now", null, bobsHat).andExpect(status().isNotFound());
        mvc.perform(delete("/api/outfits/" + outfit.getId()).header("Authorization", bearer(bob)))
                .andExpect(status().isNotFound());

        assertThat(outfits.findById(outfit.getId())).get().extracting(Outfit::getName).isEqualTo("Weekend");
    }

    @Test
    void deleteRemovesTheOutfitButKeepsTheClothes() throws Exception {
        mvc.perform(delete("/api/outfits/" + outfit.getId()).header("Authorization", bearer(alice)))
                .andExpect(status().isNoContent());

        mvc.perform(get("/api/outfits/" + outfit.getId()).header("Authorization", bearer(alice)))
                .andExpect(status().isNotFound());
        assertThat(clothing.countByOwner_Id(alice.getId())).isEqualTo(4);
    }

    // ---- helpers ----

    private ResultActions update(User user, UUID outfitId, String name, String notes, ClothingItem... items)
            throws Exception {
        String ids = String.join(",", Arrays.stream(items).map(i -> "\"" + i.getId() + "\"").toList());
        String notesJson = notes == null ? "null" : "\"" + notes + "\"";
        return mvc.perform(put("/api/outfits/" + outfitId).header("Authorization", bearer(user))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"" + name + "\",\"notes\":" + notesJson + ",\"itemIds\":[" + ids + "]}"));
    }

    private String bearer(User user) {
        return "Bearer " + jwt.createAccessToken(user.getId(), user.getEmail());
    }
}
