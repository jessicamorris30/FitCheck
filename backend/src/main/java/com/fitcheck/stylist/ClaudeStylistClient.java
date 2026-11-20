package com.fitcheck.stylist;

import com.anthropic.client.AnthropicClient;
import com.anthropic.errors.AnthropicServiceException;
import com.anthropic.models.messages.Message;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.OutputConfig;
import com.fitcheck.config.AiProperties;
import java.util.stream.Collectors;
import org.springframework.beans.factory.DisposableBean;

/**
 * The real implementation: talks to the Claude API through Anthropic's official Java SDK.
 * This is the only class in FitCheck that knows about the SDK.
 */
public class ClaudeStylistClient implements StylistClient, DisposableBean {

    private final AnthropicClient client;
    private final AiProperties properties;

    public ClaudeStylistClient(AnthropicClient client, AiProperties properties) {
        this.client = client;
        this.properties = properties;
    }

    @Override
    public boolean isConfigured() {
        return true;
    }

    @Override
    public String model() {
        return properties.model();
    }

    @Override
    public ConnectionCheck checkConnection() {
        MessageCreateParams params = MessageCreateParams.builder()
                .model(properties.model())
                // Small cap: this is a one-line answer. Opus 5.5 always thinks a little first,
                // so we leave room for that; at "low" effort it keeps it brief.
                .maxTokens(1024L)
                .outputConfig(OutputConfig.builder().effort(OutputConfig.Effort.LOW).build())
                .addUserMessage("This is a connection test from the FitCheck app. Reply with exactly: FitCheck connected")
                .build();
        try {
            Message response = client.messages().create(params);
            // A response is a list of "content blocks" (thinking, text, ...). Keep just the text.
            String reply = response.content().stream()
                    .flatMap(block -> block.text().stream())
                    .map(text -> text.text())
                    .collect(Collectors.joining())
                    .trim();
            return new ConnectionCheck(response.model().asString(), reply,
                    response.usage().inputTokens(), response.usage().outputTokens());
        } catch (AnthropicServiceException e) {
            // The API answered with an error: bad key (401), no credit, overloaded, ...
            throw new AiUnavailableException("Claude API error: " + e.getMessage(), e);
        } catch (RuntimeException e) {
            // Couldn't reach the API at all (network, timeout).
            throw new AiUnavailableException("Couldn't reach the Claude API: " + e.getMessage(), e);
        }
    }

    /** Called by Spring at shutdown: closes the SDK's connection pool and threads. */
    @Override
    public void destroy() {
        client.close();
    }
}
