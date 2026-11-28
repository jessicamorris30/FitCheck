package com.fitcheck.user;

import com.fitcheck.TestAccounts;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fitcheck.ai.AiUsage;
import com.fitcheck.ai.AiUsageRepository;
import com.fitcheck.clothing.ClothingItemRepository;
import com.fitcheck.outfit.OutfitRepository;
import com.fitcheck.storage.BlobStorageService;
import com.fitcheck.storage.TestImages;
import com.jayway.jsonpath.JsonPath;
import jakarta.servlet.http.Cookie;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

/**
 * DELETE /api/users/me with REAL commits (not @Transactional): files are only removed after the
 * database commit, which a rolled-back test never reaches. Cleans up after itself.
 */
@SpringBootTest
@AutoConfigureMockMvc
class AccountDeletionTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private UserRepository users;

    @Autowired
    private ClothingItemRepository clothing;

    @Autowired
    private OutfitRepository outfits;

    @Autowired
    private AiUsageRepository aiUsage;

    @Autowired
    private BlobStorageService storage;

    private String email;
    private UUID userId;
    private String token;
    private String refreshCookie;

    @BeforeEach
    void createAFullAccount() throws Exception {
        email = "goodbye-" + UUID.randomUUID() + "@example.com";
        MockHttpServletResponse reg = mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(TestAccounts.registerJson(email, "my-password-1")))
                .andReturn().getResponse();
        token = JsonPath.read(reg.getContentAsString(), "$.accessToken");
        userId = UUID.fromString(JsonPath.read(reg.getContentAsString(), "$.user.id"));
        refreshCookie = reg.getCookie("fitcheck_refresh").getValue();

        // A clothing item (photo + thumbnail in storage), an outfit using it, and some AI usage.
        String item = mvc.perform(multipart("/api/clothing").header("Authorization", "Bearer " + token)
                        .file(new MockMultipartFile("image", "tee.jpg", "image/jpeg", TestImages.jpeg(400, 500)))
                        .file(new MockMultipartFile("metadata", "", "application/json",
                                "{\"name\":\"Tee\",\"category\":\"TOP\"}".getBytes())))
                .andReturn().getResponse().getContentAsString();
        String itemId = JsonPath.read(item, "$.id");
        mvc.perform(post("/api/outfits").header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Look\",\"itemIds\":[\"" + itemId + "\"]}"));
        aiUsage.save(new AiUsage(users.findById(userId).orElseThrow(), Instant.now()));
    }

    @AfterEach
    void cleanUp() {
        users.findById(userId).ifPresent(users::delete);
        storage.deleteAllWithPrefix("users/" + userId + "/");
    }

    @Test
    void deletesTheAccountAndEverythingInIt() throws Exception {
        String prefix = "users/" + userId + "/";
        assertThat(storage.countWithPrefix(prefix)).isEqualTo(2); // photo + thumbnail
        assertThat(clothing.countByOwner_Id(userId)).isEqualTo(1);
        assertThat(outfits.countByOwner_Id(userId)).isEqualTo(1);

        MockHttpServletResponse response = deleteAccount("my-password-1")
                .andExpect(status().isNoContent())
                .andReturn().getResponse();

        assertThat(response.getHeader("Set-Cookie")).contains("Max-Age=0"); // browser drops the refresh cookie
        assertThat(users.findById(userId)).isEmpty();
        assertThat(clothing.countByOwner_Id(userId)).isZero();
        assertThat(outfits.countByOwner_Id(userId)).isZero();
        assertThat(aiUsage.countByUser_IdAndCreatedAtGreaterThanEqual(userId, Instant.EPOCH)).isZero();
        assertThat(storage.countWithPrefix(prefix)).isZero(); // files gone too

        // The old session and the old password are both useless now.
        mvc.perform(post("/api/auth/refresh").cookie(new Cookie("fitcheck_refresh", refreshCookie)))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(TestAccounts.registerJson(email, "my-password-1")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void wrongPasswordDeletesNothing() throws Exception {
        deleteAccount("not-my-password")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.password").value("Password is incorrect"));

        assertThat(users.findById(userId)).isPresent();
        assertThat(storage.countWithPrefix("users/" + userId + "/")).isEqualTo(2);
    }

    @Test
    void requiresLoginAndAPassword() throws Exception {
        mvc.perform(delete("/api/users/me").contentType(MediaType.APPLICATION_JSON).content("{\"password\":\"x\"}"))
                .andExpect(status().isUnauthorized());
        deleteAccount("").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.password").value("Enter your password to confirm"));
    }

    @Test
    void refusesToDeleteFilesWithAnUnsafePrefix() {
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> storage.deleteAllWithPrefix(""))
                .isInstanceOf(IllegalArgumentException.class);
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> storage.deleteAllWithPrefix("users"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private org.springframework.test.web.servlet.ResultActions deleteAccount(String password) throws Exception {
        return mvc.perform(delete("/api/users/me").header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"password\":\"" + password + "\"}"));
    }
}
