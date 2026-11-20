package com.fitcheck.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Settings for the AI stylist, under {@code fitcheck.ai.*} in application.yml.
 *
 * @param apiKey     Anthropic API key (ANTHROPIC_API_KEY in .env). Blank = AI features switched off.
 * @param model      Claude model id, e.g. {@code claude-opus-5-5}
 * @param effort     how hard Claude thinks: low / medium / high / xhigh / max. Set explicitly because
 *                   each model has its own default.
 * @param maxTokens  upper limit on the length of one answer (thinking included)
 * @param dailyLimit AI requests allowed per user per day (enforced in step 4.5)
 */
@ConfigurationProperties("fitcheck.ai")
public record AiProperties(String apiKey, String model, String effort, long maxTokens, int dailyLimit) {

    public boolean hasApiKey() {
        return apiKey != null && !apiKey.isBlank();
    }
}
