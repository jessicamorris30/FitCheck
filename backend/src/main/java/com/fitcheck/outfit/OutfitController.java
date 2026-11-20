package com.fitcheck.outfit;

import com.fitcheck.auth.AuthenticatedUser;
import com.fitcheck.outfit.dto.OutfitRequest;
import com.fitcheck.outfit.dto.OutfitResponse;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Outfit endpoints under /api/outfits. All require login; the owner always comes from the token.
 */
@RestController
@RequestMapping("/api/outfits")
public class OutfitController {

    private final OutfitService outfitService;

    public OutfitController(OutfitService outfitService) {
        this.outfitService = outfitService;
    }

    /**
     * POST /api/outfits {"name","notes","itemIds":[...]} → 201 + the outfit.
     * 400 if details are invalid or an item isn't yours.
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public OutfitResponse create(@AuthenticationPrincipal AuthenticatedUser me, @Valid @RequestBody OutfitRequest request) {
        return outfitService.create(me.id(), request);
    }

    /** GET /api/outfits → your outfits, most recently changed first. */
    @GetMapping
    public List<OutfitResponse> list(@AuthenticationPrincipal AuthenticatedUser me) {
        return outfitService.list(me.id());
    }

    /** GET /api/outfits/{id} → one of your outfits, or 404. */
    @GetMapping("/{id}")
    public OutfitResponse get(@AuthenticationPrincipal AuthenticatedUser me, @PathVariable UUID id) {
        return outfitService.get(me.id(), id);
    }

    /**
     * PUT /api/outfits/{id}, same body as create → the updated outfit.
     * Replaces name, notes and the full item list (order included). 400 invalid, 404 not yours.
     */
    @PutMapping("/{id}")
    public OutfitResponse update(
            @AuthenticationPrincipal AuthenticatedUser me,
            @PathVariable UUID id,
            @Valid @RequestBody OutfitRequest request) {
        return outfitService.update(me.id(), id, request);
    }

    /** DELETE /api/outfits/{id} → 204. Your clothes are not affected. 404 if it isn't yours. */
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal AuthenticatedUser me, @PathVariable UUID id) {
        outfitService.delete(me.id(), id);
    }
}
