package com.fitcheck.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Settings under {@code fitcheck.storage.*} (application.yml).
 *
 * @param connectionString Azure Storage connection string (Azurite locally, a real storage account in Azure)
 * @param containerName    the blob container that holds clothing images, like a top-level folder
 * @param sasTtl           how long an image link (SAS URL) handed to the browser stays valid
 * @param publicEndpoint   optional: the storage address to put in image links for browsers, when it
 *                         differs from the address the app uses (local Docker testing, a future CDN).
 *                         Empty = use the normal address.
 */
@ConfigurationProperties("fitcheck.storage")
public record StorageProperties(String connectionString, String containerName, Duration sasTtl, String publicEndpoint) {
}
