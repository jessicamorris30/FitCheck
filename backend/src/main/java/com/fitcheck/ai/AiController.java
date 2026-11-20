package com.fitcheck.ai;

import com.fitcheck.ai.dto.SuggestionsRequest;
import com.fitcheck.ai.dto.SuggestionsResponse;
import com.fitcheck.auth.AuthenticatedUser;
import com.fitcheck.stylist.StylistClient;
import jakarta.validation.Valid;
import java.io.IOException;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * AI stylist endpoints under /api/ai (login required).
 */
@RestController
@RequestMapping("/api/ai")
public class AiController {

    private final StylistClient stylist;
    private final OutfitSuggestionService suggestions;
    private final AiUsageService usageService;

    public AiController(StylistClient stylist, OutfitSuggestionService suggestions, AiUsageService usageService) {
        this.stylist = stylist;
        this.suggestions = suggestions;
        this.usageService = usageService;
    }

    /** Whether the AI stylist is switched on, and with which model. Free: it doesn't call Claude. */
    public record AiStatus(boolean configured, String model) {
    }

    /** GET /api/ai/status → {"configured": true|false, "model": "claude-opus-5-5"} */
    @GetMapping("/status")
    public AiStatus status() {
        return new AiStatus(stylist.isConfigured(), stylist.model());
    }

    /**
     * GET /api/ai/usage → {"used": 3, "limit": 20, "remaining": 17, "resetsAt": "...T00:00:00Z"}
     * For the "n of 20 left today" hint in the UI. Free: it doesn't call Claude.
     */
    @GetMapping("/usage")
    public AiUsageService.UsageSummary usage(@AuthenticationPrincipal AuthenticatedUser me) {
        return usageService.summary(me.id());
    }

    /**
     * POST /api/ai/outfit-suggestions {"prompt": "rainy-day office look"}
     * → 200 {"suggestions": [{"title", "reasoning", "items": [...]}]}  (takes ~10-40 s)
     * 400 empty prompt or empty wardrobe, 403 email not confirmed, 429 daily limit reached,
     * 422 request declined, 503 AI unavailable (that request isn't counted).
     */
    @PostMapping(value = "/outfit-suggestions", consumes = MediaType.APPLICATION_JSON_VALUE)
    public SuggestionsResponse suggest(@AuthenticationPrincipal AuthenticatedUser me,
            @Valid @RequestBody SuggestionsRequest request) {
        return suggestions.suggest(me.id(), request.prompt());
    }

    /**
     * POST /api/ai/outfit-suggestions as multipart/form-data, used when there's an inspiration photo:
     * {@code prompt} (text, optional) + {@code image} (JPEG/PNG/WebP up to 10 MB, optional); at least one.
     * Same responses as the JSON version. The photo is only sent to Claude, never stored.
     *
     * <p>Same URL, two methods: Spring picks one by the request's Content-Type ("consumes").
     */
    @PostMapping(value = "/outfit-suggestions", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public SuggestionsResponse suggestWithPhoto(@AuthenticationPrincipal AuthenticatedUser me,
            @RequestParam(value = "prompt", required = false) String prompt,
            @RequestPart(value = "image", required = false) MultipartFile image) throws IOException {
        byte[] photo = image == null || image.isEmpty() ? null : image.getBytes();
        return suggestions.suggest(me.id(), prompt, photo);
    }
}
