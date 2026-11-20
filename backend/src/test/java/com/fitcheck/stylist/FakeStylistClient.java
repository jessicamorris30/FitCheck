package com.fitcheck.stylist;

import com.fitcheck.stylist.StylistModels.SuggestionRequest;
import com.fitcheck.stylist.StylistModels.SuggestionResult;
import java.util.List;
import java.util.function.Function;

/**
 * A stand-in for Claude in tests: no network, no cost, predictable answers.
 *
 * <p>Each test decides what "Claude" answers by setting {@link #answer}, and can inspect
 * {@link #lastRequest} to check what FitCheck would have sent.
 */
public class FakeStylistClient implements StylistClient {

    /** What the next suggest() call returns (or throws), given the request. */
    public Function<SuggestionRequest, SuggestionResult> answer = req -> new SuggestionResult(List.of());

    public SuggestionRequest lastRequest;

    @Override
    public boolean isConfigured() {
        return true;
    }

    @Override
    public String model() {
        return "fake-model";
    }

    @Override
    public ConnectionCheck checkConnection() {
        return new ConnectionCheck("fake-model", "FitCheck connected", 0, 0);
    }

    @Override
    public SuggestionResult suggest(SuggestionRequest request) {
        lastRequest = request;
        return answer.apply(request);
    }
}
