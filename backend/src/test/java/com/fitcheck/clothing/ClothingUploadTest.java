package com.fitcheck.clothing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fitcheck.auth.JwtService;
import com.fitcheck.storage.BlobStorageService;
import com.fitcheck.user.User;
import com.fitcheck.user.UserRepository;
import com.jayway.jsonpath.JsonPath;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

/**
 * POST /api/clothing end to end: security, validation, Postgres and Azurite.
 * Database rows are rolled back; uploaded blobs are deleted in cleanUp().
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ClothingUploadTest {

    /** Just enough of a JPEG to pass the "first bytes" check. */
    private static final byte[] FAKE_JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 1, 2, 3, 4, 5, 6, 7, 8};

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
    private String token;

    @BeforeEach
    void logIn() {
        user = users.saveAndFlush(new User("upload-" + UUID.randomUUID() + "@example.com", "hash"));
        token = jwt.createAccessToken(user.getId(), user.getEmail());
    }

    @AfterEach
    void cleanUp() {
        items.findAllByOwner_IdOrderByCreatedAtDesc(user.getId()).forEach(i -> storage.delete(i.getBlobName()));
    }

    @Test
    void uploadsPhotoAndSavesItem() throws Exception {
        String body = upload(image(FAKE_JPEG), metadata("""
                {"name":"Blue jeans","category":"BOTTOM","color":"blue","tags":["Casual","denim"]}
                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.name").value("Blue jeans"))
                .andExpect(jsonPath("$.category").value("BOTTOM"))
                .andExpect(jsonPath("$.tags[0]").value("casual"))
                .andExpect(jsonPath("$.blobName").doesNotExist())
                .andReturn().getResponse().getContentAsString();

        // The row points at a blob under this user's folder, with the right extension...
        ClothingItem saved = items.findAllByOwner_IdOrderByCreatedAtDesc(user.getId()).getFirst();
        assertThat(saved.getBlobName()).startsWith("users/" + user.getId() + "/").endsWith(".jpg");

        // ...and the signed link really serves the uploaded bytes as a JPEG.
        String imageUrl = JsonPath.read(body, "$.imageUrl");
        HttpResponse<byte[]> photo = HttpClient.newHttpClient().send(
                HttpRequest.newBuilder(URI.create(imageUrl)).build(), HttpResponse.BodyHandlers.ofByteArray());
        assertThat(photo.statusCode()).isEqualTo(200);
        assertThat(photo.body()).isEqualTo(FAKE_JPEG);
        assertThat(photo.headers().firstValue("Content-Type")).contains("image/jpeg");
    }

    @Test
    void requiresLogin() throws Exception {
        mvc.perform(multipart("/api/clothing").file(image(FAKE_JPEG))
                        .file(metadata("{\"name\":\"x\",\"category\":\"TOP\"}")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void rejectsFilesThatAreNotImages() throws Exception {
        // Claims to be a JPEG, but the bytes say otherwise.
        MockMultipartFile fake = new MockMultipartFile("image", "photo.jpg", "image/jpeg", "MZ not an image".getBytes());

        upload(fake, metadata("{\"name\":\"Shirt\",\"category\":\"TOP\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Only JPEG, PNG or WebP images are allowed"));
        assertThat(items.countByOwner_Id(user.getId())).isZero();
    }

    @Test
    void rejectsInvalidDetails() throws Exception {
        upload(image(FAKE_JPEG), metadata("{\"name\":\"\",\"category\":null,\"tags\":[\"" + "x".repeat(31) + "\"]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.name").value("Name is required"))
                .andExpect(jsonPath("$.fieldErrors.category").value("Category is required"))
                .andExpect(jsonPath("$.fieldErrors['tags[0]']").value("Each tag must be at most 30 characters"));
    }

    @Test
    void rejectsUnknownCategory() throws Exception {
        upload(image(FAKE_JPEG), metadata("{\"name\":\"Cape\",\"category\":\"CAPE\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void rejectsMissingPhoto() throws Exception {
        mvc.perform(multipart("/api/clothing").file(metadata("{\"name\":\"Shirt\",\"category\":\"TOP\"}"))
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Missing required part: image"));
    }

    // ---- helpers ----

    private ResultActions upload(MockMultipartFile image, MockMultipartFile metadata) throws Exception {
        MockMultipartHttpServletRequestBuilder request = multipart("/api/clothing").file(image).file(metadata);
        return mvc.perform(request.header("Authorization", "Bearer " + token));
    }

    private static MockMultipartFile image(byte[] bytes) {
        return new MockMultipartFile("image", "photo.jpg", "image/jpeg", bytes);
    }

    private static MockMultipartFile metadata(String json) {
        return new MockMultipartFile("metadata", "", "application/json", json.getBytes());
    }
}
