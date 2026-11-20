package com.fitcheck.clothing;

import com.fitcheck.clothing.dto.ClothingItemRequest;
import com.fitcheck.clothing.dto.ClothingItemResponse;
import com.fitcheck.common.exception.BadRequestException;
import com.fitcheck.common.exception.NotFoundException;
import com.fitcheck.storage.BlobStorageService;
import com.fitcheck.storage.ImageType;
import com.fitcheck.storage.ThumbnailService;
import com.fitcheck.user.User;
import com.fitcheck.user.UserRepository;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

/**
 * Wardrobe business logic: create, list, view, edit and delete items. Every method takes the owner's
 * id (from the access token) and only ever touches that owner's items.
 */
@Service
public class ClothingService {

    private static final Logger log = LoggerFactory.getLogger(ClothingService.class);

    private final ClothingItemRepository items;
    private final UserRepository users;
    private final BlobStorageService storage;
    private final ThumbnailService thumbnails;

    public ClothingService(ClothingItemRepository items, UserRepository users, BlobStorageService storage,
            ThumbnailService thumbnails) {
        this.items = items;
        this.users = users;
        this.storage = storage;
        this.thumbnails = thumbnails;
    }

    /**
     * Stores the photo and a small thumbnail in Blob Storage, then saves the item row pointing at both.
     *
     * <p>Order matters:
     * <ol>
     *   <li>Make the thumbnail first. Decoding the whole image is also a real check that the file
     *       is a working image (the magic-bytes check only looks at the first few bytes).
     *   <li>Upload the photo and the thumbnail.
     *   <li>Save the row. If that fails, delete both uploads again so no orphaned files pile up.
     * </ol>
     *
     * @throws BadRequestException if the file is empty, not a JPEG/PNG/WebP, or can't be decoded
     */
    @Transactional
    public ClothingItemResponse create(UUID ownerId, ClothingItemRequest request, MultipartFile image) {
        ImageType type = detectImageType(image);
        byte[] original = readBytes(image);
        byte[] thumbnail;
        try {
            thumbnail = thumbnails.toJpeg(original, ThumbnailService.THUMBNAIL_SIZE);
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("We couldn't read that image. Please try a different photo.");
        }

        // users/<ownerId>/<random>.jpg: random so names never collide or reveal anything.
        String baseName = "users/" + ownerId + "/" + UUID.randomUUID();
        String blobName = baseName + "." + type.extension();
        String thumbName = baseName + "_thumb.jpg";

        storage.upload(blobName, original, type.contentType());
        try {
            storage.upload(thumbName, thumbnail, "image/jpeg");
            // getReferenceById: a placeholder for the user without loading the row; we only need its id.
            User owner = users.getReferenceById(ownerId);
            ClothingItem item = new ClothingItem(
                    owner, request.name(), request.category(), request.color(), request.tags(), blobName);
            item.setThumbnailBlobName(thumbName);
            return toResponse(items.saveAndFlush(item));
        } catch (RuntimeException e) {
            log.warn("Saving clothing item failed; removing uploaded blobs {} and {}", blobName, thumbName);
            storage.delete(blobName);
            storage.delete(thumbName);
            throw e;
        }
    }

    /**
     * Makes and stores a thumbnail for an item that doesn't have one yet (uploaded before
     * thumbnails existed). Used by ThumbnailBackfill. Does nothing if it already has one.
     */
    @Transactional
    public void createMissingThumbnail(UUID itemId) {
        ClothingItem item = items.findById(itemId).orElse(null);
        if (item == null || item.getThumbnailBlobName() != null) {
            return;
        }
        String thumbName = item.getBlobName().replaceFirst("\\.[^.]+$", "") + "_thumb.jpg";
        byte[] thumbnail = thumbnails.toJpeg(storage.download(item.getBlobName()), ThumbnailService.THUMBNAIL_SIZE);
        storage.upload(thumbName, thumbnail, "image/jpeg");
        item.setThumbnailBlobName(thumbName);
    }

    /**
     * The owner's wardrobe, newest first, optionally only one category.
     * Each item gets a fresh signed image link (computed locally: no call to storage).
     */
    @Transactional(readOnly = true)
    public List<ClothingItemResponse> list(UUID ownerId, ClothingCategory category) {
        List<ClothingItem> found = category == null
                ? items.findAllByOwner_IdOrderByCreatedAtDesc(ownerId)
                : items.findAllByOwner_IdAndCategoryOrderByCreatedAtDesc(ownerId, category);
        return found.stream().map(this::toResponse).toList();
    }

    /**
     * One item. Someone else's item and a non-existent one look exactly the same (404),
     * so the API never confirms that another user's item exists.
     *
     * @throws NotFoundException if there's no such item owned by this user
     */
    @Transactional(readOnly = true)
    public ClothingItemResponse get(UUID ownerId, UUID itemId) {
        return toResponse(findOwned(ownerId, itemId));
    }

    /**
     * Changes an item's details (name, category, color, tags). The photo stays the same.
     *
     * <p>No explicit save() needed: the item was loaded inside this transaction, so Hibernate
     * notices the changes ("dirty checking") and writes an UPDATE when the transaction commits.
     *
     * @throws NotFoundException if there's no such item owned by this user
     */
    @Transactional
    public ClothingItemResponse update(UUID ownerId, UUID itemId, ClothingItemRequest request) {
        ClothingItem item = findOwned(ownerId, itemId);
        item.updateDetails(request.name(), request.category(), request.color(), request.tags());
        return toResponse(item);
    }

    /**
     * Deletes an item and its photo.
     *
     * <p>Order matters again (two systems, no shared transaction). The row is deleted now, but the
     * photo only after the database has committed. If anything fails before that, the row is
     * rolled back and its photo is still there, so you never end up with an item whose photo is
     * gone. The worst case is the reverse: a leftover photo nobody points to, which is harmless.
     *
     * <p>Outfits that used this item lose it automatically (ON DELETE CASCADE on outfit_items).
     *
     * @throws NotFoundException if there's no such item owned by this user
     */
    @Transactional
    public void delete(UUID ownerId, UUID itemId) {
        ClothingItem item = findOwned(ownerId, itemId);
        List<String> blobs = new java.util.ArrayList<>(List.of(item.getBlobName()));
        if (item.getThumbnailBlobName() != null) {
            blobs.add(item.getThumbnailBlobName());
        }
        items.delete(item);

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                for (String blob : blobs) {
                    try {
                        storage.delete(blob);
                    } catch (RuntimeException e) {
                        log.warn("Item deleted but its file {} could not be removed", blob, e);
                    }
                }
            }
        });
    }

    private ClothingItem findOwned(UUID ownerId, UUID itemId) {
        return items.findByIdAndOwner_Id(itemId, ownerId)
                .orElseThrow(() -> new NotFoundException("Clothing item not found"));
    }

    private ClothingItemResponse toResponse(ClothingItem item) {
        return ClothingItemResponse.from(item, storage);
    }

    private static byte[] readBytes(MultipartFile image) {
        try {
            return image.getBytes(); // at most 10 MB (multipart limit), fine to hold in memory
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read uploaded file", e);
        }
    }

    private static ImageType detectImageType(MultipartFile image) {
        if (image == null || image.isEmpty()) {
            throw new BadRequestException("Please choose a photo to upload");
        }
        try (InputStream in = image.getInputStream()) {
            return ImageType.detect(in)
                    .orElseThrow(() -> new BadRequestException("Only JPEG, PNG or WebP images are allowed"));
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read uploaded file", e);
        }
    }
}
