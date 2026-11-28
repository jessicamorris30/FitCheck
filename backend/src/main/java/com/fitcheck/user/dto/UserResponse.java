package com.fitcheck.user.dto;

import com.fitcheck.user.User;
import java.time.Instant;
import java.util.UUID;

/**
 * What the API shows about a user. Deliberately leaves out passwordHash: entities are never
 * returned directly from controllers, so a new sensitive column can't leak by accident.
 *
 * @param username        lower case; null for accounts from before Phase 7 until they set one
 * @param emailVerified   whether the user has clicked the link in their confirmation email
 * @param profileComplete false until username, first and last name are all set (the app then
 *                        asks for them before anything else)
 * @param theme           "system", "light" or "dark"; null if never chosen (= follow the device)
 * @param admin           whether this is an admin account (Phase 8), so the app can show the Admin
 *                        link. Only ever about the user themselves; the server checks again on
 *                        every admin request.
 */
public record UserResponse(
        UUID id,
        String email,
        String username,
        String firstName,
        String lastName,
        boolean profileComplete,
        String theme,
        boolean admin,
        boolean emailVerified,
        Instant createdAt) {

    /** @param admin from AdminAccess.isAdmin(user): callers must decide, so it's never left out by mistake */
    public static UserResponse from(User user, boolean admin) {
        return new UserResponse(user.getId(), user.getEmail(), user.getUsername(), user.getFirstName(),
                user.getLastName(), user.isProfileComplete(), user.getTheme(), admin, user.isEmailVerified(),
                user.getCreatedAt());
    }
}
