package com.fitcheck.storage;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Keeps recently used thumbnails in memory, so asking the AI twice in a row doesn't download
 * every thumbnail from Blob Storage twice.
 *
 * <p>Safe to cache forever per name: a thumbnail's file never changes (a new photo gets a new
 * random name). Holds at most {@link #MAX_ENTRIES} thumbnails (~20 KB each, so ~40 MB at most);
 * the least recently used ones are dropped first ("LRU").
 */
@Component
public class ThumbnailCache {

    static final int MAX_ENTRIES = 2000;

    private final BlobStorageService storage;

    /** LinkedHashMap in "access order" + removeEldestEntry = a simple LRU cache. */
    private final Map<String, byte[]> cache = Collections.synchronizedMap(new LinkedHashMap<>(256, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, byte[]> eldest) {
            return size() > MAX_ENTRIES;
        }
    });

    public ThumbnailCache(BlobStorageService storage) {
        this.storage = storage;
    }

    public byte[] get(String blobName) {
        byte[] cached = cache.get(blobName);
        if (cached != null) {
            return cached;
        }
        byte[] downloaded = storage.download(blobName);
        cache.put(blobName, downloaded);
        return downloaded;
    }
}
