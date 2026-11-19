package com.fitcheck.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * Turns on {@code @Async}: methods with that annotation run on a background thread, so the
 * caller doesn't wait for them. Used to send emails without slowing down sign-up.
 */
@Configuration
@EnableAsync
public class AsyncConfig {
}
