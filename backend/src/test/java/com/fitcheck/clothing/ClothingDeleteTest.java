package com.fitcheck.clothing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fitcheck.auth.JwtService;
import com.fitcheck.storage.BlobStorageService;
import com.fitcheck.storage.TestImages;
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

    @Autowired
    private ClothingService clothingService;

    private User user;
    private ClothingItem item;

    @BeforeEach
    void setUp() {
        user = users.save(new User("delete-" + UUID.randomUUID() + "@example.com", "hash"));
        String blobName = "users/" + user.getId() + "/" + UUID.randomUUID() + ".jpg";
        byte[] photo = TestImages.jpeg(640, 480);
        storage.upload(blobName, new ByteArrayInputStream(photo), photo.length, "image/jpeg");
        String thumbName = blobName.replace(".jpg", "_thumb.jpg");
        storage.upload(thumbName, TestImages.jpeg(320, 240), "image/jpeg");
        ClothingItem toSave = new ClothingItem(user, "Old sweater", ClothingCategory.TOP, null, List.of(), blobName);
        toSave.setThumbnailBlobName(thumbName);
        item = items.save(toSave);
    }

    @AfterEach
    void cleanUp() {
        storage.delete(item.getBlobName());
        storage.delete(item.getThumbnailBlobName());
        users.deleteById(user.getId()); // ON DELETE CASCADE removes any remaining items
    }

    @Test
    void deletesTheRowThePhotoAndTheThumbnail() throws Exception {
        assertThat(storage.exists(item.getBlobName())).isTrue();
        assertThat(storage.exists(item.getThumbnailBlobName())).isTrue();

        mvc.perform(delete("/api/clothing/" + item.getId())
                        .header("Authorization", "Bearer " + jwt.createAccessToken(user.getId(), user.getEmail())))
                .andExpect(status().isNoContent());

        assertThat(items.findById(item.getId())).isEmpty();
        assertThat(storage.exists(item.getBlobName())).isFalse();
        assertThat(storage.exists(item.getThumbnailBlobName())).isFalse();
    }

    @Test
    void olderItemWithoutThumbnailGetsOne() {
        // An item uploaded before thumbnails existed: a photo, but no thumbnail.
        String oldBlob = "users/" + user.getId() + "/" + UUID.randomUUID() + ".png";
        storage.upload(oldBlob, TestImages.png(900, 1200), "image/png");
        ClothingItem old = items.save(new ClothingItem(user, "Vintage coat", ClothingCategory.OUTERWEAR, null, List.of(), oldBlob));
        assertThat(items.findIdsWithoutThumbnail()).contains(old.getId());

        clothingService.createMissingThumbnail(old.getId());

        ClothingItem reloaded = items.findById(old.getId()).orElseThrow();
        assertThat(reloaded.getThumbnailBlobName()).endsWith("_thumb.jpg");
        assertThat(TestImages.decode(storage.download(reloaded.getThumbnailBlobName())).getHeight()).isEqualTo(320);
        assertThat(items.findIdsWithoutThumbnail()).doesNotContain(old.getId());

        storage.delete(oldBlob);
        storage.delete(reloaded.getThumbnailBlobName());
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
