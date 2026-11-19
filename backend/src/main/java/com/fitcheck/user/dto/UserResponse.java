package com.fitcheck.user.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * Public view of a user.
 */
public record UserResponse(UUID id, String email, Instant createdAt) {
}
