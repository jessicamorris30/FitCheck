package com.fitcheck.style;

import com.fitcheck.auth.AuthenticatedUser;
import com.fitcheck.style.dto.StyleRequest;
import com.fitcheck.style.dto.StyleResponse;
import jakarta.validation.Valid;
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
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * "My style" under /api/users/me/style. Always the logged-in user's own style (from the token).
 * Every answer is the whole style as the browser may see it (no AI notes, see StyleResponse).
 */
@RestController
@RequestMapping("/api/users/me/style")
public class StyleController {

    private final StyleService styles;

    public StyleController(StyleService styles) {
        this.styles = styles;
    }

    /** GET → your note, switch and photos. */
    @GetMapping
    public StyleResponse get(@AuthenticationPrincipal AuthenticatedUser me) {
        return styles.get(me.id());
    }

    /** PUT {"note","useWithAi"} → the style. 400 if the note is over 1000 characters. */
    @PutMapping
    public StyleResponse update(@AuthenticationPrincipal AuthenticatedUser me, @Valid @RequestBody StyleRequest request) {
        return styles.update(me.id(), request);
    }

    /** POST multipart "image" → 201 + the style. 400 not an image / already 5 photos. */
    @PostMapping(path = "/photos", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public StyleResponse addPhoto(@AuthenticationPrincipal AuthenticatedUser me, @RequestPart("image") MultipartFile image) {
        return styles.addPhoto(me.id(), image);
    }

    /** DELETE /photos/{id} → the style without it. 404 if it isn't yours. */
    @DeleteMapping("/photos/{id}")
    public StyleResponse deletePhoto(@AuthenticationPrincipal AuthenticatedUser me, @PathVariable UUID id) {
        return styles.deletePhoto(me.id(), id);
    }
}
