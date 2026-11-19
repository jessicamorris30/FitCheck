package com.fitcheck.storage;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Talks to the real Azurite emulator from docker compose (like the other tests use the real
 * Postgres), so it needs {@code docker compose up -d} first. It downloads through the SAS link
 * with a plain HTTP client, exactly like a browser would.
 */
@SpringBootTest
class BlobStorageServiceTest {

    private static final HttpClient http = HttpClient.newHttpClient();

    @Autowired
    private BlobStorageService storage;

    private final String blobName = "tests/" + UUID.randomUUID() + ".txt";

    @AfterEach
    void cleanUp() {
        storage.delete(blobName);
    }

    @Test
    void uploadedFileCanBeDownloadedThroughTheSignedLink() throws Exception {
        byte[] content = "a pretend photo".getBytes();
        storage.upload(blobName, new ByteArrayInputStream(content), content.length, "text/plain");

        HttpResponse<byte[]> response = get(storage.readUrl(blobName));

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).isEqualTo(content);
        assertThat(response.headers().firstValue("Content-Type")).contains("text/plain");
    }

    @Test
    void linkWithoutSignatureIsRefused() throws Exception {
        byte[] content = "private".getBytes();
        storage.upload(blobName, new ByteArrayInputStream(content), content.length, "text/plain");

        String unsigned = storage.readUrl(blobName).split("\\?")[0];

        // The container is private: the plain blob address alone gives nothing away.
        assertThat(get(unsigned).statusCode()).isNotEqualTo(200);
    }

    @Test
    void signedLinkIsReadOnly() throws Exception {
        byte[] content = "original".getBytes();
        storage.upload(blobName, new ByteArrayInputStream(content), content.length, "text/plain");

        HttpRequest overwrite = HttpRequest.newBuilder(URI.create(storage.readUrl(blobName)))
                .header("x-ms-blob-type", "BlockBlob")
                .PUT(HttpRequest.BodyPublishers.ofString("hacked"))
                .build();

        assertThat(http.send(overwrite, HttpResponse.BodyHandlers.discarding()).statusCode()).isEqualTo(403);
        assertThat(get(storage.readUrl(blobName)).body()).isEqualTo(content);
    }

    @Test
    void deleteRemovesTheFileAndIsSafeToRepeat() {
        byte[] content = "bye".getBytes();
        storage.upload(blobName, new ByteArrayInputStream(content), content.length, "text/plain");
        assertThat(storage.exists(blobName)).isTrue();

        storage.delete(blobName);
        storage.delete(blobName); // already gone: no error

        assertThat(storage.exists(blobName)).isFalse();
    }

    private static HttpResponse<byte[]> get(String url) throws Exception {
        return http.send(HttpRequest.newBuilder(URI.create(url)).GET().build(), HttpResponse.BodyHandlers.ofByteArray());
    }
}
