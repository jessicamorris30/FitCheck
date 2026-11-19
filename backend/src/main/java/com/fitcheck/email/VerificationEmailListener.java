package com.fitcheck.email;

import com.fitcheck.auth.VerificationEmailRequested;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Sends the confirmation email when a {@link VerificationEmailRequested} event is published.
 *
 * <ul>
 *   <li>{@code AFTER_COMMIT}: runs only once the sign-up has been saved to the database. If the
 *       sign-up fails and is rolled back, no email goes out for an account that doesn't exist.
 *   <li>{@code @Async}: runs on a background thread. The user gets their sign-up response
 *       immediately instead of waiting for the mail server.
 *   <li>try/catch: if the mail server is down, sign-up still succeeds; the user can press
 *       "Resend" later. We log the failure so it isn't silent.
 * </ul>
 */
@Component
public class VerificationEmailListener {

    private static final Logger log = LoggerFactory.getLogger(VerificationEmailListener.class);

    private final EmailSender emailSender;

    public VerificationEmailListener(EmailSender emailSender) {
        this.emailSender = emailSender;
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onVerificationEmailRequested(VerificationEmailRequested event) {
        try {
            emailSender.sendVerificationEmail(event.email(), event.verifyUrl());
            log.info("Sent verification email to {}", event.email());
        } catch (RuntimeException e) {
            log.error("Failed to send verification email to {}", event.email(), e);
        }
    }
}
