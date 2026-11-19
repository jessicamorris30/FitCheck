package com.fitcheck.auth;

/**
 * An "event": a message saying "a confirmation email should go out". EmailVerificationService
 * publishes it, and VerificationEmailListener reacts to it by actually sending the email.
 *
 * <p>Splitting it this way lets the email wait until the database transaction has been saved
 * (see the listener), and keeps mail-sending code out of the sign-up logic.
 *
 * @param email     where to send it
 * @param verifyUrl the full link, containing the one-time token
 */
public record VerificationEmailRequested(String email, String verifyUrl) {
}
