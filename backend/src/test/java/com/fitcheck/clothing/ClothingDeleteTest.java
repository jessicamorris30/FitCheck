package com.fitcheck.clothing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fitcheck.auth.JwtService;
import com.fitcheck.storage.BlobStorageService;
import com.fitcheck.user.User;
import com.fitcheck.user.UserRepository;
import java.io.ByteArrayInputStream;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

/**
 * DELETE /api/clothing/{id}, with REAL commits.
 *
 * <p>Unlike the other tests this class is not @Transactional, because the photo is only removed
 * after the database commits, and a test transaction that's rolled back never commits. So this
 * test cleans up its own data in cleanUp().
 */
@SpringBootTest
@AutoConfigureMockMvc
class ClothingDeleteTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private UserRepository users;

    @Autowired
    private ClothingItemRepository items;

    @Autowired
    private BlobStorageService storage;

    @Autowired
    private JwtService jwt;

    private User user;
    private ClothingItem item;

    @BeforeEach
    void setUp() {
        user = users.save(new User("delete-" + UUID.randomUUID() + "@example.com", "hash"));
        String blobName = "users/" + user.getId() + "/" + UUID.randomUUID() + ".jpg";
        byte[] photo = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 1, 2, 3};
        storage.upload(blobName, new ByteArrayInputStream(photo), photo.length, "image/jpeg");
        item = items.save(new ClothingItem(user, "Old sweater", ClothingCategory.TOP, null, List.of(), blobName));
    }

    @AfterEach
    void cleanUp() {
        storage.delete(item.getBlobName());
        users.deleteById(user.getId()); // ON DELETE CASCADE removes any remaining items
    }

    @Test
    void deletesTheRowAndThePhoto() throws Exception {
        assertThat(storage.exists(item.getBlobName())).isTrue();

        mvc.perform(delete("/api/clothing/" + item.getId())
                        .header("Authorization", "Bearer " + jwt.createAccessToken(user.getId(), user.getEmail())))
                .andExpect(status().isNoContent());

        assertThat(items.findById(item.getId())).isEmpty();
        assertThat(storage.exists(item.getBlobName())).isFalse();
    }

    @Test
    void deletingTwiceIsA404TheSecondTime() throws Exception {
        String auth = "Bearer " + jwt.createAccessToken(user.getId(), user.getEmail());
        mvc.perform(delete("/api/clothing/" + item.getId()).header("Authorization", auth))
                .andExpect(status().isNoContent());
        mvc.perform(delete("/api/clothing/" + item.getId()).header("Authorization", auth))
                .andExpect(status().isNotFound());
    }
}
