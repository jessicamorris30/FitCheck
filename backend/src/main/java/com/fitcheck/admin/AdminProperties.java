package com.fitcheck.admin;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * {@code fitcheck.admin.*}: who is admin. Set with the ADMIN_EMAILS environment variable on the
 * server (comma-separated), so the admin's address never appears in the public repository.
 *
 * @param emails the admin accounts' emails; empty = nobody is admin
 */
@ConfigurationProperties("fitcheck.admin")
public record AdminProperties(List<String> emails) {

    /** The emails in one form (trimmed, lower case), for comparing. */
    public Set<String> normalizedEmails() {
        if (emails == null) {
            return Set.of();
        }
        return emails.stream()
                .filter(e -> e != null && !e.isBlank())
                .map(e -> e.trim().toLowerCase(Locale.ROOT))
                .collect(Collectors.toUnmodifiableSet());
    }
}
