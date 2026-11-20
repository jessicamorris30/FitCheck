package com.fitcheck.ai;

import com.fitcheck.stylist.StylistClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * AI stylist endpoints under /api/ai (login required). Step 4.2: a status check.
 * Step 4.3 adds POST /api/ai/outfit-suggestions.
 */
@RestController
@RequestMapping("/api/ai")
public class AiController {

    private final StylistClient stylist;

    public AiController(StylistClient stylist) {
        this.stylist = stylist;
    }

    /** Whether the AI stylist is switched on, and with which model. Free: it doesn't call Claude. */
    public record AiStatus(boolean configured, String model) {
    }

    /** GET /api/ai/status → {"configured": true|false, "model": "claude-opus-5-5"} */
    @GetMapping("/status")
    public AiStatus status() {
        return new AiStatus(stylist.isConfigured(), stylist.model());
    }
}
