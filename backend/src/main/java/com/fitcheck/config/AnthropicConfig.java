package com.fitcheck.config;

import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.fitcheck.stylist.ClaudeStylistClient;
import com.fitcheck.stylist.NotConfiguredStylistClient;
import com.fitcheck.stylist.StylistClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Decides which {@link StylistClient} the app uses:
 * a real Claude client when ANTHROPIC_API_KEY is set, otherwise a "not configured" one that
 * answers AI requests with 503 while everything else keeps working.
 *
 * <p>The key is only ever read here, on the server. It is never sent to the browser.
 */
@Configuration
public class AnthropicConfig {

    private static final Logger log = LoggerFactory.getLogger(AnthropicConfig.class);

    @Bean
    public StylistClient stylistClient(AiProperties properties) {
        if (!properties.hasApiKey()) {
            log.warn("ANTHROPIC_API_KEY is not set: the AI stylist is switched off.");
            return new NotConfiguredStylistClient(properties.model());
        }
        log.info("AI stylist enabled with model {}", properties.model());
        return new ClaudeStylistClient(
                AnthropicOkHttpClient.builder().apiKey(properties.apiKey()).build(), properties);
    }
}
