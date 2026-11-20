package com.fitcheck.email;

import com.fitcheck.config.EmailProperties;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.web.util.HtmlUtils;

/**
 * Builds and sends FitCheck's emails over SMTP.
 *
 * <p>{@link JavaMailSender} is created by Spring Boot from the {@code spring.mail.*} settings:
 * Mailpit on localhost:1025 in development, a real provider (e.g. Azure Communication
 * Services) in production. This class doesn't know or care which.
 */
@Service
public class EmailSender {

    private final JavaMailSender mailSender;
    private final EmailProperties properties;

    public EmailSender(JavaMailSender mailSender, EmailProperties properties) {
        this.mailSender = mailSender;
        this.properties = properties;
    }

    public void sendVerificationEmail(String to, String verifyUrl) {
        String text = """
                Welcome to FitCheck!

                Please confirm your email address by opening this link:
                %s

                The link is valid for 24 hours. If you didn't create a FitCheck account, you can ignore this email.
                """.formatted(verifyUrl);

        String safeUrl = HtmlUtils.htmlEscape(verifyUrl); // never put raw values into HTML
        String html = """
                <div style="font-family:Arial,sans-serif;max-width:480px;margin:auto;color:#111">
                  <h1 style="font-size:22px">Welcome to FitCheck!</h1>
                  <p>Please confirm your email address to finish setting up your account.</p>
                  <p style="margin:28px 0">
                    <a href="%s" style="background:#111;color:#fff;padding:12px 20px;border-radius:8px;text-decoration:none">
                      Confirm my email
                    </a>
                  </p>
                  <p style="font-size:13px;color:#666">Or paste this link into your browser:<br>%s</p>
                  <p style="font-size:13px;color:#666">The link is valid for 24 hours. If you didn't create a FitCheck account, you can ignore this email.</p>
                </div>
                """.formatted(safeUrl, safeUrl);

        send(to, "Confirm your FitCheck email", text, html);
    }

    public void sendPasswordResetEmail(String to, String resetUrl) {
        String text = """
                Someone (hopefully you) asked to reset the password for your FitCheck account.

                To choose a new password, open this link:
                %s

                The link works once and is valid for 1 hour. If you didn't ask for this, you can ignore \
                this email: your password stays the same.
                """.formatted(resetUrl);

        String safeUrl = HtmlUtils.htmlEscape(resetUrl);
        String html = """
                <div style="font-family:Arial,sans-serif;max-width:480px;margin:auto;color:#111">
                  <h1 style="font-size:22px">Reset your FitCheck password</h1>
                  <p>Someone (hopefully you) asked to reset the password for your FitCheck account.</p>
                  <p style="margin:28px 0">
                    <a href="%s" style="background:#111;color:#fff;padding:12px 20px;border-radius:8px;text-decoration:none">
                      Choose a new password
                    </a>
                  </p>
                  <p style="font-size:13px;color:#666">Or paste this link into your browser:<br>%s</p>
                  <p style="font-size:13px;color:#666">The link works once and is valid for 1 hour. If you didn't ask for this, you can ignore this email: your password stays the same.</p>
                </div>
                """.formatted(safeUrl, safeUrl);

        send(to, "Reset your FitCheck password", text, html);
    }

    /** Sends a message with both a plain-text and an HTML version; mail apps show the best one they support. */
    private void send(String to, String subject, String text, String html) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(properties.from());
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(text, html);
            mailSender.send(message);
        } catch (MessagingException e) {
            throw new IllegalStateException("Could not build email to " + to, e);
        }
    }
}
