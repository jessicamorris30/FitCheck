package com.fitcheck.email;

import com.fitcheck.auth.PasswordResetRequested;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/** Sends the reset email after the reset link has been saved (same pattern as VerificationEmailListener). */
@Component
public class PasswordResetEmailListener {

    private static final Logger log = LoggerFactory.getLogger(PasswordResetEmailListener.class);

    private final EmailSender emailSender;

    public PasswordResetEmailListener(EmailSender emailSender) {
        this.emailSender = emailSender;
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onPasswordResetRequested(PasswordResetRequested event) {
        try {
            emailSender.sendPasswordResetEmail(event.email(), event.resetUrl());
            log.info("Sent password reset email to {}", event.email());
        } catch (RuntimeException e) {
            log.error("Failed to send password reset email to {}", event.email(), e);
        }
    }
}
