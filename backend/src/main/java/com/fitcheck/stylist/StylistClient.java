package com.fitcheck.stylist;

/**
 * Everything FitCheck asks of the AI, in FitCheck's own terms.
 *
 * <p>Why an interface instead of calling the Anthropic SDK directly from the services?
 * <ul>
 *   <li>Tests can plug in a fake implementation, so running the test suite never calls the real
 *       API (no cost, no network, predictable answers).
 *   <li>All Claude-specific code lives in one class ({@link ClaudeStylistClient}).
 *   <li>Without an API key, a "not configured" implementation is used, and the rest of the app
 *       keeps working.
 * </ul>
 */
public interface StylistClient {

    /** False when no API key is set; AI features then answer 503. */
    boolean isConfigured();

    /** The Claude model in use, e.g. "claude-opus-5-5". */
    String model();

    /**
     * Sends one tiny request to prove the key and model work. Costs a fraction of a cent.
     *
     * @throws AiUnavailableException if not configured or the API can't be reached
     */
    ConnectionCheck checkConnection();

    /**
     * Asks Claude for 1-3 outfits made from the given wardrobe that match the prompt.
     * The result may still contain ids that aren't in the wardrobe; the caller must check them.
     *
     * @throws AiRefusedException    if Claude declines the request
     * @throws AiUnavailableException if not configured, the API fails, or the answer is unusable
     */
    StylistModels.SuggestionResult suggest(StylistModels.SuggestionRequest request);

    /** Result of {@link #checkConnection()}: what Claude replied and how many tokens it took. */
    record ConnectionCheck(String model, String reply, long inputTokens, long outputTokens) {
    }
}
