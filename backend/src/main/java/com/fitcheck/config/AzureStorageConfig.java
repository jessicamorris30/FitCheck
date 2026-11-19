package com.fitcheck.config;

import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.BlobServiceClientBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Creates the Azure Blob Storage client from {@code fitcheck.storage.*}.
 *
 * <p>Blob Storage basics: a storage <b>account</b> contains <b>containers</b> (like top-level
 * folders), which contain <b>blobs</b> (files). FitCheck uses one private container,
 * "clothing-images". Nothing in it is publicly readable; the browser only gets short-lived
 * signed links (see BlobStorageService).
 *
 * <p>Building the client doesn't contact Azure yet, so the app (and its tests) can start even
 * when storage is unreachable. The container is created on first use instead.
 */
@Configuration
public class AzureStorageConfig {

    @Bean
    public BlobContainerClient clothingImagesContainer(StorageProperties properties) {
        if (properties.connectionString() == null || properties.connectionString().isBlank()) {
            throw new IllegalStateException(
                    "fitcheck.storage.connection-string is not set. Set AZURE_STORAGE_CONNECTION_STRING in your .env file.");
        }
        return new BlobServiceClientBuilder()
                .connectionString(properties.connectionString())
                .buildClient()
                .getBlobContainerClient(properties.containerName());
    }
}
