package com.fitcheck.user.dto;

import com.fitcheck.user.User;
import java.time.Instant;
import java.util.UUID;

/**
 * What the API shows about a user. Deliberately leaves out passwordHash: entities are never
 * returned directly from controllers, so a new sensitive column can't leak by accident.
 */
public record UserResponse(UUID id, String email, Instant createdAt) {

    public static UserResponse from(User user) {
        return new UserResponse(user.getId(), user.getEmail(), user.getCreatedAt());
    }
}
