package com.fitcheck.clothing;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fitcheck.auth.JwtService;
import com.fitcheck.user.User;
import com.fitcheck.user.UserRepository;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

/**
 * GET /api/clothing and GET /api/clothing/{id}. Items are created straight in the database here
 * (no real photos needed): signed links are computed from the blob name alone.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ClothingQueryTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private UserRepository users;

    @Autowired
    private ClothingItemRepository items;

    @Autowired
    private JwtService jwt;

    private User alice;
    private User bob;
    private ClothingItem aliceShirt;
    private ClothingItem bobCoat;

    @BeforeEach
    void setUp() {
        alice = users.saveAndFlush(new User("alice-" + UUID.randomUUID() + "@example.com", "hash"));
        bob = users.saveAndFlush(new User("bob-" + UUID.randomUUID() + "@example.com", "hash"));
        aliceShirt = save(alice, "White shirt", ClothingCategory.TOP);
        save(alice, "Sneakers", ClothingCategory.SHOES);
        save(alice, "Boots", ClothingCategory.SHOES);
        bobCoat = save(bob, "Bob's coat", ClothingCategory.OUTERWEAR);
    }

    private ClothingItem save(User owner, String name, ClothingCategory category) {
        return items.saveAndFlush(new ClothingItem(owner, name, category, null, List.of("tag"),
                "users/" + owner.getId() + "/" + UUID.randomUUID() + ".jpg"));
    }

    @Test
    void listReturnsOnlyMyItemsWithImageLinks() throws Exception {
        as(alice, "/api/clothing")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(3)))
                .andExpect(jsonPath("$[*].name", containsInAnyOrder("White shirt", "Sneakers", "Boots")))
                // The SDK URL-encodes "/" inside blob names as %2F; Azure treats both the same.
                .andExpect(jsonPath("$[0].imageUrl", containsString("users%2F" + alice.getId() + "%2F")))
                .andExpect(jsonPath("$[0].imageUrl", containsString("sig=")));
    }

    @Test
    void listCanFilterByCategory() throws Exception {
        as(alice, "/api/clothing?category=SHOES")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].name", containsInAnyOrder("Sneakers", "Boots")));
    }

    @Test
    void emptyWardrobeIsAnEmptyList() throws Exception {
        User carol = users.saveAndFlush(new User("carol-" + UUID.randomUUID() + "@example.com", "hash"));
        as(carol, "/api/clothing").andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void getReturnsMyItem() throws Exception {
        as(alice, "/api/clothing/" + aliceShirt.getId())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("White shirt"))
                .andExpect(jsonPath("$.category").value("TOP"))
                .andExpect(jsonPath("$.imageUrl", containsString("sig=")));
    }

    @Test
    void someoneElsesItemLooksLikeItDoesNotExist() throws Exception {
        as(alice, "/api/clothing/" + bobCoat.getId())
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Clothing item not found"));
        as(alice, "/api/clothing/" + UUID.randomUUID()).andExpect(status().isNotFound());
    }

    @Test
    void badValuesAreA400NotA500() throws Exception {
        as(alice, "/api/clothing/not-a-uuid")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Invalid value for 'id'"));
        as(alice, "/api/clothing?category=CAPE")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Invalid value for 'category'"));
    }

    @Test
    void requiresLogin() throws Exception {
        mvc.perform(get("/api/clothing")).andExpect(status().isUnauthorized());
    }

    private ResultActions as(User user, String url) throws Exception {
        String token = jwt.createAccessToken(user.getId(), user.getEmail());
        return mvc.perform(get(url).header("Authorization", "Bearer " + token));
    }
}
