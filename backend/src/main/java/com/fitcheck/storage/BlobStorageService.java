package com.fitcheck.storage;

import com.azure.storage.blob.BlobClient;
import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.models.BlobHttpHeaders;
import com.azure.storage.blob.models.ListBlobsOptions;
import com.azure.storage.blob.options.BlobParallelUploadOptions;
import com.azure.storage.blob.sas.BlobSasPermission;
import com.azure.storage.blob.sas.BlobServiceSasSignatureValues;
import com.fitcheck.config.StorageProperties;
import java.io.InputStream;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.springframework.stereotype.Service;

/**
 * Stores and serves clothing images in Azure Blob Storage.
 *
 * <p>The database only keeps each image's <b>blob name</b> (e.g. {@code users/<id>/<uuid>.jpg}),
 * never a URL. Whenever the API returns an item, it asks this service for a fresh
 * <b>SAS URL</b> ("Shared Access Signature"): the blob's address plus a signature that grants
 * read-only access until a short expiry. That way the container stays private, links can't be
 * reused forever, and images load straight from Azure without passing through our server.
 */
@Service
public class BlobStorageService {

    private final BlobContainerClient container;
    private final StorageProperties properties;
    private final Clock clock;

    /** Set once the container is known to exist, so we only check on the first upload. */
    private volatile boolean containerReady;

    public BlobStorageService(BlobContainerClient container, StorageProperties properties, Clock clock) {
        this.container = container;
        this.properties = properties;
        this.clock = clock;
    }

    /**
     * Uploads a file, replacing any existing blob with the same name.
     *
     * @param blobName    where to store it, e.g. {@code users/<userId>/<uuid>.jpg}
     * @param data        the file contents
     * @param size        number of bytes in {@code data}
     * @param contentType e.g. {@code image/jpeg}; browsers use it to display the image correctly
     */
    public void upload(String blobName, InputStream data, long size, String contentType) {
        ensureContainer();
        BlobClient blob = container.getBlobClient(blobName);
        BlobParallelUploadOptions options = new BlobParallelUploadOptions(data)
                .setHeaders(new BlobHttpHeaders().setContentType(contentType));
        blob.uploadWithResponse(options, null, null);
    }

    /** Uploads small in-memory content (e.g. a generated thumbnail). */
    public void upload(String blobName, byte[] data, String contentType) {
        upload(blobName, new java.io.ByteArrayInputStream(data), data.length, contentType);
    }

    /** Downloads a whole blob into memory. Only for small-ish files like photos (≤ 10 MB). */
    public byte[] download(String blobName) {
        return container.getBlobClient(blobName).downloadContent().toBytes();
    }

    /** Deletes a blob. Deleting something that's already gone is not an error. */
    public void delete(String blobName) {
        container.getBlobClient(blobName).deleteIfExists();
    }

    /**
     * Deletes every blob whose name starts with this prefix, e.g. {@code users/<id>/} = all of one
     * user's photos and thumbnails, including any leftovers that no database row points to anymore.
     *
     * @return how many blobs were deleted
     */
    public int deleteAllWithPrefix(String prefix) {
        if (prefix == null || prefix.isBlank() || !prefix.endsWith("/")) {
            // Guard rail: an empty or partial prefix could match far more than intended.
            throw new IllegalArgumentException("Prefix must be a non-empty folder path ending in '/'");
        }
        if (!container.exists()) {
            return 0;
        }
        int deleted = 0;
        for (var blob : container.listBlobs(new ListBlobsOptions().setPrefix(prefix), null)) {
            if (container.getBlobClient(blob.getName()).deleteIfExists()) {
                deleted++;
            }
        }
        return deleted;
    }

    /** How many blobs start with this prefix (for tests). */
    public int countWithPrefix(String prefix) {
        if (!container.exists()) {
            return 0;
        }
        int count = 0;
        for (var ignored : container.listBlobs(new ListBlobsOptions().setPrefix(prefix), null)) {
            count++;
        }
        return count;
    }

    /** True if a blob with this name exists (mainly for tests and cleanup jobs). */
    public boolean exists(String blobName) {
        return container.getBlobClient(blobName).exists();
    }

    /**
     * A read-only link to this blob that expires after {@code fitcheck.storage.sas-ttl}.
     * Creating it is pure computation with the account key: no network call.
     */
    public String readUrl(String blobName) {
        BlobClient blob = container.getBlobClient(blobName);
        OffsetDateTime expiresAt = OffsetDateTime.now(clock.withZone(ZoneOffset.UTC)).plus(properties.sasTtl());
        BlobServiceSasSignatureValues sas = new BlobServiceSasSignatureValues(
                expiresAt, new BlobSasPermission().setReadPermission(true));
        return publicAddress(blob.getBlobUrl()) + "?" + blob.generateSas(sas);
    }

    /**
     * Links handed to browsers normally use the same storage address the app uses. If
     * {@code fitcheck.storage.public-endpoint} is set, links use that address instead: the
     * signature only covers WHICH file and permissions, not the host name, so it stays valid.
     * Used when the browser must reach storage differently from the app (local Docker testing),
     * or later to serve images through a CDN.
     */
    private String publicAddress(String blobUrl) {
        String publicEndpoint = properties.publicEndpoint();
        if (publicEndpoint == null || publicEndpoint.isBlank()) {
            return blobUrl;
        }
        String accountUrl = container.getAccountUrl();
        return blobUrl.startsWith(accountUrl)
                ? publicEndpoint.replaceAll("/+$", "") + blobUrl.substring(accountUrl.length())
                : blobUrl;
    }

    /** Creates the private container the first time it's needed. */
    private void ensureContainer() {
        if (!containerReady) {
            container.createIfNotExists(); // private by default: no anonymous access
            containerReady = true;
        }
    }
}
