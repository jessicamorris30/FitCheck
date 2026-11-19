package com.fitcheck.clothing;

import com.fitcheck.clothing.dto.ClothingItemRequest;
import com.fitcheck.clothing.dto.ClothingItemResponse;
import com.fitcheck.common.exception.BadRequestException;
import com.fitcheck.storage.BlobStorageService;
import com.fitcheck.storage.ImageType;
import com.fitcheck.user.User;
import com.fitcheck.user.UserRepository;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/**
 * Wardrobe business logic. Step 2.3: creating items (photo + details).
 */
@Service
public class ClothingService {

    private static final Logger log = LoggerFactory.getLogger(ClothingService.class);

    private final ClothingItemRepository items;
    private final UserRepository users;
    private final BlobStorageService storage;

    public ClothingService(ClothingItemRepository items, UserRepository users, BlobStorageService storage) {
        this.items = items;
        this.users = users;
        this.storage = storage;
    }

    /**
     * Stores the photo in Blob Storage, then saves the item row pointing at it.
     *
     * <p>Two systems are involved and they can't share one transaction, so the order matters:
     * upload the photo first, then save the row. If saving the row fails, we delete the photo
     * again so no orphaned files pile up.
     *
     * @throws BadRequestException if the file is empty or not a JPEG, PNG or WebP image
     */
    @Transactional
    public ClothingItemResponse create(UUID ownerId, ClothingItemRequest request, MultipartFile image) {
        ImageType type = detectImageType(image);

        // users/<ownerId>/<random>.jpg: random so names never collide or reveal anything.
        String blobName = "users/" + ownerId + "/" + UUID.randomUUID() + "." + type.extension();

        try (InputStream data = image.getInputStream()) {
            storage.upload(blobName, data, image.getSize(), type.contentType());
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read uploaded file", e);
        }

        try {
            // getReferenceById: a placeholder for the user without loading the row; we only need its id.
            User owner = users.getReferenceById(ownerId);
            ClothingItem item = items.saveAndFlush(new ClothingItem(
                    owner, request.name(), request.category(), request.color(), request.tags(), blobName));
            return ClothingItemResponse.from(item, storage.readUrl(blobName));
        } catch (RuntimeException e) {
            log.warn("Saving clothing item failed; removing uploaded blob {}", blobName);
            storage.delete(blobName);
            throw e;
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
