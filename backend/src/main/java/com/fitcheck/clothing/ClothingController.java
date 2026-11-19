package com.fitcheck.clothing;

import com.fitcheck.auth.AuthenticatedUser;
import com.fitcheck.clothing.dto.ClothingItemRequest;
import com.fitcheck.clothing.dto.ClothingItemResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
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
}
