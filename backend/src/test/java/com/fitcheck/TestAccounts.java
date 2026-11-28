package com.fitcheck;

import java.util.UUID;

/** Shared helpers for tests that sign up through the real API. */
public final class TestAccounts {

    private TestAccounts() {
    }

    /**
     * A complete sign-up body (email, password, a unique username, first and last name), for tests
     * that only care about the email and password.
     */
    public static String registerJson(String email, String password) {
        return "{\"email\":\"" + email + "\",\"password\":\"" + password + "\","
                + "\"username\":\"" + uniqueUsername() + "\",\"firstName\":\"Test\",\"lastName\":\"User\"}";
    }

    /** "t" + 12 random hex characters: valid, and never the same twice. */
    public static String uniqueUsername() {
        return "t" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    }
}
