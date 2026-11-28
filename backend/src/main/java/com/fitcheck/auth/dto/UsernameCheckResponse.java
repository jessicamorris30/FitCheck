package com.fitcheck.auth.dto;

/**
 * Answer of {@code POST /api/auth/check-username}.
 *
 * @param username  the username as it would be stored (lower case, trimmed)
 * @param available true if it can be taken right now
 * @param message   why not, when it isn't (for showing under the field); null when available
 */
public record UsernameCheckResponse(String username, boolean available, String message) {
}
