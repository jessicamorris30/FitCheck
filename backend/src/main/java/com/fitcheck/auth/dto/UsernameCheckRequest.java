package com.fitcheck.auth.dto;

import com.fitcheck.user.ProfileRules;

/**
 * JSON body of {@code POST /api/auth/check-username}: {@code { "username": "..." }}.
 * Not validated with annotations: a badly formed username is a normal answer ("not available,
 * because ..."), not an error.
 */
public record UsernameCheckRequest(String username) {

    public UsernameCheckRequest {
        username = ProfileRules.normalizeUsername(username);
    }
}
