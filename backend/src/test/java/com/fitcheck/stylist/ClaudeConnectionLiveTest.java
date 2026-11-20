package com.fitcheck.stylist;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Calls the REAL Claude API once (a fraction of a cent). Tagged "live", so the normal test run
 * skips it; run it on purpose with:
 *
 * <pre>.\mvnw.cmd test -Dtest=ClaudeConnectionLiveTest -Dtest.excludedGroups=none</pre>
 *
 * It's skipped (not failed) if no ANTHROPIC_API_KEY is configured.
 */
@Tag("live")
@SpringBootTest
class ClaudeConnectionLiveTest {

    @Autowired
    private StylistClient stylist;

    @Test
    void claudeAnswers() {
        assumeTrue(stylist.isConfigured(), "ANTHROPIC_API_KEY not set in .env, skipping");

        StylistClient.ConnectionCheck check = stylist.checkConnection();

        System.out.printf("%nClaude (%s) replied: \"%s\"  [%d input / %d output tokens]%n%n",
                check.model(), check.reply(), check.inputTokens(), check.outputTokens());
        assertThat(check.reply()).containsIgnoringCase("connected");
    }
}
