package com.fitcheck.config;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * One shared source of "now". Classes that check expiry ask for a {@link Clock} instead of
 * calling Instant.now() directly, so tests can swap in a clock set to any time they want.
 */
@Configuration
public class ClockConfig {

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
