package com.fitcheck.clothing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/** PUT /api/clothing/{id} and the "not yours" rules for PUT and DELETE. Rolled back after each test. */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ClothingEditTest {

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

    @BeforeEach
    void setUp() {
        alice = users.saveAndFlush(new User("alice-" + UUID.randomUUID() + "@example.com", "hash"));
        bob = users.saveAndFlush(new User("bob-" + UUID.randomUUID() + "@example.com", "hash"));
        aliceShirt = items.saveAndFlush(new ClothingItem(alice, "White shirt", ClothingCategory.TOP, "white",
                List.of("casual"), "users/" + alice.getId() + "/" + UUID.randomUUID() + ".jpg"));
    }

    @Test
    void updatesDetailsButKeepsThePhoto() throws Exception {
        String blobBefore = aliceShirt.getBlobName();

        mvc.perform(put("/api/clothing/" + aliceShirt.getId()).header("Authorization", bearer(alice))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Linen shirt","category":"TOP","color":"cream","tags":["Summer","WORK"]}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Linen shirt"))
                .andExpect(jsonPath("$.color").value("cream"))
                .andExpect(jsonPath("$.tags[0]").value("summer"))
                .andExpect(jsonPath("$.tags[1]").value("work"));

        ClothingItem reloaded = items.findById(aliceShirt.getId()).orElseThrow();
        assertThat(reloaded.getName()).isEqualTo("Linen shirt");
        assertThat(reloaded.getBlobName()).isEqualTo(blobBefore);
    }

    @Test
    void rejectsInvalidDetails() throws Exception {
        mvc.perform(put("/api/clothing/" + aliceShirt.getId()).header("Authorization", bearer(alice))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"\",\"category\":\"TOP\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.name").value("Name is required"));
    }

    @Test
    void cannotEditOrDeleteSomeoneElsesItem() throws Exception {
        mvc.perform(put("/api/clothing/" + aliceShirt.getId()).header("Authorization", bearer(bob))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Mine now\",\"category\":\"TOP\"}"))
                .andExpect(status().isNotFound());
        mvc.perform(delete("/api/clothing/" + aliceShirt.getId()).header("Authorization", bearer(bob)))
                .andExpect(status().isNotFound());

        assertThat(items.findById(aliceShirt.getId())).get()
                .extracting(ClothingItem::getName).isEqualTo("White shirt");
    }

    @Test
    void requiresLogin() throws Exception {
        mvc.perform(delete("/api/clothing/" + aliceShirt.getId())).andExpect(status().isUnauthorized());
    }

    private String bearer(User user) {
        return "Bearer " + jwt.createAccessToken(user.getId(), user.getEmail());
    }
}
