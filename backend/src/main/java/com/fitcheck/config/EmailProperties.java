package com.fitcheck.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Settings under {@code fitcheck.mail.*}. (The SMTP server itself is configured under
 * {@code spring.mail.*}, which Spring Boot reads on its own.)
 *
 * @param from            the sender shown on emails, e.g. {@code FitCheck <no-reply@fitcheck.app>}
 * @param frontendUrl     base URL of the React app; email links point here
 * @param verificationTtl how long a confirmation link stays valid
 * @param resendCooldown  minimum time between two confirmation (or reset) emails for one user
 * @param passwordResetTtl how long a "reset your password" link stays valid
 */
@ConfigurationProperties("fitcheck.mail")
public record EmailProperties(String from, String frontendUrl, Duration verificationTtl, Duration resendCooldown,
        Duration passwordResetTtl) {
}
