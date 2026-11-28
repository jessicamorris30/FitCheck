package com.fitcheck.style;

import com.fitcheck.common.exception.BadRequestException;
import com.fitcheck.common.exception.NotFoundException;
import com.fitcheck.storage.BlobStorageService;
import com.fitcheck.storage.ImageTooLargeException;
import com.fitcheck.storage.ImageType;
import com.fitcheck.storage.ThumbnailService;
import com.fitcheck.style.dto.StyleRequest;
import com.fitcheck.style.dto.StyleResponse;
import com.fitcheck.user.UserRepository;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

/**
 * "My style": the user's note, the on/off switch and up to 5 style photos.
 *
 * <p>Every change to the photos or the note bumps the style's version and publishes
 * {@link StyleChanged}; {@link StyleDescriber} then rewrites Claude's private notes in the
 * background. Nothing here ever returns those notes.
 */
@Service
public class StyleService {

    private static final Logger log = LoggerFactory.getLogger(StyleService.class);

    public static final int MAX_PHOTOS = 5;
    /** Style photos are kept at this size: plenty for Claude and the page, and no huge originals. */
    static final int PHOTO_MAX_SIDE = 1024;

    private final UserStyleRepository styles;
    private final StyleImageRepository photos;
    private final UserRepository users;
    private final BlobStorageService storage;
    private final ThumbnailService resizer;
    private final ApplicationEventPublisher events;

    public StyleService(UserStyleRepository styles, StyleImageRepository photos, UserRepository users,
            BlobStorageService storage, ThumbnailService resizer, ApplicationEventPublisher events) {
        this.styles = styles;
        this.photos = photos;
        this.users = users;
        this.storage = storage;
        this.resizer = resizer;
        this.events = events;
    }

    /** The user's style (empty, with the switch on, if they've never set anything). */
    @Transactional(readOnly = true)
    public StyleResponse get(UUID userId) {
        UserStyle style = styles.findById(userId).orElse(null);
        List<StyleResponse.Photo> list = photos.findAllByOwner_IdOrderByCreatedAtAscIdAsc(userId).stream()
                .map(p -> new StyleResponse.Photo(p.getId(), storage.readUrl(p.getBlobName()),
                        storage.readUrl(p.getThumbnailBlobName()), p.getCreatedAt()))
                .toList();
        return style == null
                ? new StyleResponse(null, true, list, MAX_PHOTOS, null)
                : new StyleResponse(style.getNote(), style.isUseWithAi(), list, MAX_PHOTOS, style.getUpdatedAt());
    }

    /** Saves the note and the switch. Only a changed note means the private notes need rewriting. */
    @Transactional
    public StyleResponse update(UUID userId, StyleRequest request) {
        UserStyle style = lockedStyle(userId);
        boolean noteChanged = style.changeNote(request.note());
        style.setUseWithAi(request.useWithAi());
        if (noteChanged) {
            changed(style);
        }
        styles.flush();
        return get(userId);
    }

    /**
     * Adds a style photo: checked like clothing photos, then re-saved as a 1024 px JPEG (which also
     * drops hidden metadata such as GPS location) plus a 320 px thumbnail.
     *
     * @throws BadRequestException if it isn't a JPEG/PNG/WebP image, can't be read, is enormous, or
     *                             the user already has 5 photos
     */
    @Transactional
    public StyleResponse addPhoto(UUID userId, MultipartFile image) {
        checkIsImage(image);
        // Lock first, so two uploads at once can't both see "4 photos" and end up with 6.
        UserStyle style = lockedStyle(userId);
        if (photos.countByOwner_Id(userId) >= MAX_PHOTOS) {
            throw new BadRequestException("You can add up to " + MAX_PHOTOS + " style photos. Remove one first.");
        }

        byte[] large;
        byte[] thumbnail;
        try {
            byte[] original = image.getBytes();
            large = resizer.toJpeg(original, PHOTO_MAX_SIDE);
            thumbnail = resizer.toJpeg(original, ThumbnailService.THUMBNAIL_SIZE);
        } catch (ImageTooLargeException e) {
            throw new BadRequestException("That photo has too many pixels (max 100 megapixels). Please use a smaller one.");
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("We couldn't read that image. Please try a different photo.");
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read uploaded file", e);
        }

        // users/<id>/style/<random>.jpg: inside the user's folder, so deleting the account removes it.
        String base = "users/" + userId + "/style/" + UUID.randomUUID();
        String blobName = base + ".jpg";
        String thumbName = base + "_thumb.jpg";
        storage.upload(blobName, large, "image/jpeg");
        try {
            storage.upload(thumbName, thumbnail, "image/jpeg");
            photos.saveAndFlush(new StyleImage(users.getReferenceById(userId), blobName, thumbName));
            changed(style);
            styles.flush();
        } catch (RuntimeException e) {
            log.warn("Saving style photo failed; removing uploaded files {} and {}", blobName, thumbName);
            storage.delete(blobName);
            storage.delete(thumbName);
            throw e;
        }
        return get(userId);
    }

    /**
     * Removes a style photo (its files go after the change is saved).
     *
     * @throws NotFoundException if there's no such photo of this user's
     */
    @Transactional
    public StyleResponse deletePhoto(UUID userId, UUID photoId) {
        StyleImage photo = photos.findByIdAndOwner_Id(photoId, userId)
                .orElseThrow(() -> new NotFoundException("Style photo not found"));
        UserStyle style = lockedStyle(userId);
        List<String> files = List.of(photo.getBlobName(), photo.getThumbnailBlobName());
        photos.delete(photo);
        changed(style);
        styles.flush();

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                for (String file : files) {
                    try {
                        storage.delete(file);
                    } catch (RuntimeException e) {
                        log.warn("Style photo deleted but its file {} could not be removed", file, e);
                    }
                }
            }
        });
        return get(userId);
    }

    /** The user's style row, locked for this transaction; created on first use. */
    private UserStyle lockedStyle(UUID userId) {
        return styles.findForUpdateByUserId(userId).orElseGet(() -> styles.saveAndFlush(new UserStyle(userId)));
    }

    /** Photos or note changed: new version, and Claude's private notes need rewriting (after commit). */
    private void changed(UserStyle style) {
        style.markChanged();
        events.publishEvent(new StyleChanged(style.getUserId(), style.getVersion()));
    }

    private static void checkIsImage(MultipartFile image) {
        if (image == null || image.isEmpty()) {
            throw new BadRequestException("Please choose a photo to upload");
        }
        try (InputStream in = image.getInputStream()) {
            ImageType.detect(in).orElseThrow(() -> new BadRequestException("Only JPEG, PNG or WebP images are allowed"));
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read uploaded file", e);
        }
    }
}
