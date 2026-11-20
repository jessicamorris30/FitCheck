package com.fitcheck.outfit;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fitcheck.auth.JwtService;
import com.fitcheck.clothing.ClothingCategory;
import com.fitcheck.clothing.ClothingItem;
import com.fitcheck.clothing.ClothingItemRepository;
import com.fitcheck.user.User;
import com.fitcheck.user.UserRepository;
import com.jayway.jsonpath.JsonPath;
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

/** POST / GET /api/outfits end to end. Rolled back after each test. */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class OutfitApiTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private UserRepository users;

    @Autowired
    private ClothingItemRepository clothing;

    @Autowired
    private JwtService jwt;

    private User alice;
    private User bob;
    private ClothingItem tee;
    private ClothingItem jeans;
    private ClothingItem sneakers;
    private ClothingItem bobsHat;

    @BeforeEach
    void setUp() {
        alice = users.saveAndFlush(new User("alice-" + UUID.randomUUID() + "@example.com", "hash"));
        bob = users.saveAndFlush(new User("bob-" + UUID.randomUUID() + "@example.com", "hash"));
        tee = item(alice, "White tee", ClothingCategory.TOP);
        jeans = item(alice, "Blue jeans", ClothingCategory.BOTTOM);
        sneakers = item(alice, "Sneakers", ClothingCategory.SHOES);
        bobsHat = item(bob, "Bob's hat", ClothingCategory.ACCESSORY);
    }

    private ClothingItem item(User owner, String name, ClothingCategory category) {
        return clothing.saveAndFlush(new ClothingItem(owner, name, category, null, List.of(),
                "users/" + owner.getId() + "/" + UUID.randomUUID() + ".jpg"));
    }

    @Test
    void createsOutfitKeepingTheOrderSent() throws Exception {
        create(alice, "Weekend casual", "comfy", sneakers, tee, jeans)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.name").value("Weekend casual"))
                .andExpect(jsonPath("$.notes").value("comfy"))
                .andExpect(jsonPath("$.items[*].name").value(org.hamcrest.Matchers.contains("Sneakers", "White tee", "Blue jeans")))
                .andExpect(jsonPath("$.items[0].imageUrl", containsString("sig=")));
    }

    @Test
    void cannotUseSomeoneElsesItem() throws Exception {
        create(alice, "Sneaky", null, tee, bobsHat)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("One or more items aren't in your wardrobe"));
    }

    @Test
    void validatesNameAndItems() throws Exception {
        mvc.perform(post("/api/outfits").header("Authorization", bearer(alice))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"\",\"itemIds\":[]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.name").value("Name is required"))
                .andExpect(jsonPath("$.fieldErrors.itemIds").value("Add at least one item"));
    }

    @Test
    void duplicateIdsAreOnlyUsedOnce() throws Exception {
        create(alice, "Double", null, tee, tee, jeans)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.items", hasSize(2)));
    }

    @Test
    void listAndGetAreOwnerScoped() throws Exception {
        String body = create(alice, "Alice look", null, tee).andReturn().getResponse().getContentAsString();
        String aliceOutfitId = JsonPath.read(body, "$.id");
        create(alice, "Alice look 2", null, jeans);
        create(bob, "Bob look", null, bobsHat);

        mvc.perform(get("/api/outfits").header("Authorization", bearer(alice)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].name", containsInAnyOrder("Alice look", "Alice look 2")));

        mvc.perform(get("/api/outfits/" + aliceOutfitId).header("Authorization", bearer(alice)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].name").value("White tee"));

        mvc.perform(get("/api/outfits/" + aliceOutfitId).header("Authorization", bearer(bob)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Outfit not found"));
    }

    @Test
    void requiresLogin() throws Exception {
        mvc.perform(get("/api/outfits")).andExpect(status().isUnauthorized());
    }

    // ---- helpers ----

    private ResultActions create(User user, String name, String notes, ClothingItem... items) throws Exception {
        String ids = String.join(",", java.util.Arrays.stream(items).map(i -> "\"" + i.getId() + "\"").toList());
        String notesJson = notes == null ? "null" : "\"" + notes + "\"";
        return mvc.perform(post("/api/outfits").header("Authorization", bearer(user))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"" + name + "\",\"notes\":" + notesJson + ",\"itemIds\":[" + ids + "]}"));
    }

    private String bearer(User user) {
        return "Bearer " + jwt.createAccessToken(user.getId(), user.getEmail());
    }
}
