package com.fitcheck.admin;

import com.fitcheck.user.User;
import com.fitcheck.user.UserRepository;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Decides who is admin (Phase 8). There is no "role" stored anywhere: an account is admin only
 * while ALL of these hold:
 * <ul>
 *   <li>its email is in the ADMIN_EMAILS setting on the server (never in the public repo);</li>
 *   <li>that email is confirmed (so nobody can sign up with the admin's address and be admin);</li>
 *   <li>it isn't frozen.</li>
 * </ul>
 * Removing the email from the setting removes admin; there's nothing else to forget to undo.
 */
@Component
public class AdminAccess {

    private final Set<String> adminEmails;
    private final UserRepository users;

    public AdminAccess(AdminProperties properties, UserRepository users) {
        this.adminEmails = properties.normalizedEmails();
        this.users = users;
    }

    public boolean isAdmin(User user) {
        return user != null
                && user.isEmailVerified()
                && !user.isFrozen()
                && adminEmails.contains(user.getEmail().trim().toLowerCase(Locale.ROOT));
    }

    /** Looks the account up fresh, so a change (frozen, setting removed) counts immediately. */
    public boolean isAdmin(UUID userId) {
        return userId != null && users.findById(userId).map(this::isAdmin).orElse(false);
    }
}
