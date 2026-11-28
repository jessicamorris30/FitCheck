package com.fitcheck.outfit;

import com.fitcheck.clothing.ClothingItem;
import com.fitcheck.clothing.ClothingItemRepository;
import com.fitcheck.clothing.dto.ClothingItemResponse;
import com.fitcheck.common.exception.BadRequestException;
import com.fitcheck.common.exception.NotFoundException;
import com.fitcheck.outfit.dto.OutfitRequest;
import com.fitcheck.outfit.dto.OutfitResponse;
import com.fitcheck.storage.BlobStorageService;
import com.fitcheck.user.UserRepository;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Outfit business logic. Every method takes the owner's id (from the access token) and only
 * touches that owner's outfits and clothing.
 */
@Service
public class OutfitService {

    private final OutfitRepository outfits;
    private final ClothingItemRepository clothing;
    private final UserRepository users;
    private final BlobStorageService storage;

    public OutfitService(OutfitRepository outfits, ClothingItemRepository clothing, UserRepository users,
            BlobStorageService storage) {
        this.outfits = outfits;
        this.clothing = clothing;
        this.users = users;
        this.storage = storage;
    }

    /**
     * Creates an outfit from the owner's clothing items, keeping the order they were sent in.
     *
     * @throws BadRequestException if any item id isn't one of the owner's items
     */
    @Transactional
    public OutfitResponse create(UUID ownerId, OutfitRequest request) {
        List<ClothingItem> items = loadOwnedItemsInOrder(ownerId, request.itemIds());

        Outfit outfit = new Outfit(users.getReferenceById(ownerId), request.name(), request.notes());
        outfit.replaceItems(items);
        outfit.setAiPrompt(cleanPrompt(request.aiPrompt()));
        return toResponse(outfits.saveAndFlush(outfit));
    }

    /** All of the owner's outfits, most recently changed first. */
    @Transactional(readOnly = true)
    public List<OutfitResponse> list(UUID ownerId) {
        return outfits.findAllByOwner_IdOrderByUpdatedAtDesc(ownerId).stream().map(this::toResponse).toList();
    }

    /** @throws NotFoundException if there's no such outfit owned by this user */
    @Transactional(readOnly = true)
    public OutfitResponse get(UUID ownerId, UUID outfitId) {
        return toResponse(findOwned(ownerId, outfitId));
    }

    /**
     * Replaces an outfit's name, notes and items (same body as create).
     * Items are diffed by {@link Outfit#replaceItems}, so reordering is cheap and safe.
     *
     * @throws NotFoundException   if there's no such outfit owned by this user
     * @throws BadRequestException if any item id isn't one of the owner's items
     */
    @Transactional
    public OutfitResponse update(UUID ownerId, UUID outfitId, OutfitRequest request) {
        Outfit outfit = findOwned(ownerId, outfitId);
        List<ClothingItem> items = loadOwnedItemsInOrder(ownerId, request.itemIds());

        outfit.rename(request.name(), request.notes());
        outfit.replaceItems(items);
        outfit.setAiPrompt(cleanPrompt(request.aiPrompt())); // PUT replaces everything, this too
        outfit.markChanged();
        outfits.flush(); // write now so the response shows the final updatedAt
        return toResponse(outfit);
    }

    /**
     * Deletes an outfit. Its rows in outfit_items go with it (cascade); the clothing items
     * themselves are untouched.
     *
     * @throws NotFoundException if there's no such outfit owned by this user
     */
    @Transactional
    public void delete(UUID ownerId, UUID outfitId) {
        outfits.delete(findOwned(ownerId, outfitId));
    }

    /**
     * The outfit, only if it's this owner's (also used by the calendar).
     *
     * @throws NotFoundException if there's no such outfit owned by this user
     */
    public Outfit findOwned(UUID ownerId, UUID outfitId) {
        return outfits.findByIdAndOwner_Id(outfitId, ownerId)
                .orElseThrow(() -> new NotFoundException("Outfit not found"));
    }

    /**
     * Turns the requested ids into the owner's clothing items, in the requested order.
     *
     * <p>One query fetches them all. If fewer come back than were asked for, some id either
     * doesn't exist or belongs to another user; we refuse without saying which, so this can't be
     * used to probe for other people's item ids.
     */
    List<ClothingItem> loadOwnedItemsInOrder(UUID ownerId, List<UUID> itemIds) {
        Set<UUID> unique = new LinkedHashSet<>(itemIds); // keeps order, drops duplicates
        Map<UUID, ClothingItem> found = clothing.findAllByIdInAndOwner_Id(unique, ownerId).stream()
                .collect(Collectors.toMap(ClothingItem::getId, Function.identity()));
        if (found.size() != unique.size()) {
            throw new BadRequestException("One or more items aren't in your wardrobe");
        }
        return unique.stream().map(found::get).toList();
    }

    private static String cleanPrompt(String aiPrompt) {
        return aiPrompt == null || aiPrompt.isBlank() ? null : aiPrompt.trim();
    }

    /** The outfit as sent to the browser, with signed photo links (also used by the calendar). */
    public OutfitResponse toResponse(Outfit outfit) {
        List<ClothingItemResponse> items = outfit.getClothingItems().stream()
                .map(c -> ClothingItemResponse.from(c, storage))
                .toList();
        return new OutfitResponse(outfit.getId(), outfit.getName(), outfit.getNotes(), outfit.getAiPrompt(), items,
                outfit.getCreatedAt(), outfit.getUpdatedAt());
    }
}
