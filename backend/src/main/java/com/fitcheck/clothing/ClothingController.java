package com.fitcheck.clothing;

import com.fitcheck.auth.AuthenticatedUser;
import com.fitcheck.clothing.dto.ClothingItemRequest;
import com.fitcheck.clothing.dto.ClothingItemResponse;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * Wardrobe endpoints under /api/clothing. All require login (SecurityConfig), and the owner
 * always comes from the access token, never from the request.
 */
@RestController
@RequestMapping("/api/clothing")
public class ClothingController {

    private final ClothingService clothingService;

    public ClothingController(ClothingService clothingService) {
        this.clothingService = clothingService;
    }

    /**
     * POST /api/clothing, as {@code multipart/form-data} with two parts:
     * <ul>
     *   <li>{@code image}: the photo file (JPEG, PNG or WebP, up to 10 MB)
     *   <li>{@code metadata}: JSON {@code {"name","category","color","tags"}} (Content-Type application/json)
     * </ul>
     * "Multipart" is how browsers send files: one request, several named parts, each with its own type.
     *
     * @return 201 + the new item with a signed image link; 400 for a bad file or details; 413 if too large
     */
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public ClothingItemResponse upload(
            @AuthenticationPrincipal AuthenticatedUser me,
            @RequestPart("image") MultipartFile image,
            @Valid @RequestPart("metadata") ClothingItemRequest metadata) {
        return clothingService.create(me.id(), metadata, image);
    }

    /**
     * GET /api/clothing → your whole wardrobe, newest first.
     * GET /api/clothing?category=SHOES → just one category. An unknown category → 400.
     */
    @GetMapping
    public List<ClothingItemResponse> list(
            @AuthenticationPrincipal AuthenticatedUser me,
            @RequestParam(required = false) ClothingCategory category) {
        return clothingService.list(me.id(), category);
    }

    /** GET /api/clothing/{id} → one of your items, or 404 (also for other users' items). */
    @GetMapping("/{id}")
    public ClothingItemResponse get(@AuthenticationPrincipal AuthenticatedUser me, @PathVariable UUID id) {
        return clothingService.get(me.id(), id);
    }

    /**
     * PUT /api/clothing/{id} with JSON {"name","category","color","tags"} → the updated item.
     * Replaces all editable details (fields left out become empty). 400 invalid, 404 not yours.
     */
    @PutMapping("/{id}")
    public ClothingItemResponse update(
            @AuthenticationPrincipal AuthenticatedUser me,
            @PathVariable UUID id,
            @Valid @RequestBody ClothingItemRequest request) {
        return clothingService.update(me.id(), id, request);
    }

    /** DELETE /api/clothing/{id} → 204, item and photo removed. 404 if it isn't yours. */
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal AuthenticatedUser me, @PathVariable UUID id) {
        clothingService.delete(me.id(), id);
    }
}
