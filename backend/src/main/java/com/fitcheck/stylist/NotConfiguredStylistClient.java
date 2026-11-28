package com.fitcheck.stylist;

/**
 * Used when no ANTHROPIC_API_KEY is set. The app starts and works normally; only AI requests
 * fail, with a clear 503 instead of a crash at startup.
 */
public class NotConfiguredStylistClient implements StylistClient {

    static final String MESSAGE = "The AI stylist isn't configured on this server.";

    private final String model;

    public NotConfiguredStylistClient(String model) {
        this.model = model;
    }

    @Override
    public boolean isConfigured() {
        return false;
    }

    @Override
    public String model() {
        return model;
    }

    @Override
    public ConnectionCheck checkConnection() {
        throw new AiUnavailableException(MESSAGE);
    }

    @Override
    public StylistModels.SuggestionResult suggest(StylistModels.SuggestionRequest request) {
        throw new AiUnavailableException(MESSAGE);
    }

    @Override
    public String describeStyle(StylistModels.StyleSample sample) {
        throw new AiUnavailableException(MESSAGE);
    }
}
