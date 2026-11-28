package com.fitcheck.user;

import java.util.Locale;
import java.util.Set;

/**
 * The rules for usernames and names, shared by sign-up and profile editing so they can't drift
 * apart. The regex constants are used in the request DTOs' validation annotations.
 */
public final class ProfileRules {

    /**
     * 3-30 characters: lower-case letters, numbers, '.' and '_', starting with a letter or number.
     * Never '@': login treats anything with '@' as an email.
     */
    public static final String USERNAME_REGEX = "^[a-z0-9][a-z0-9._]{2,29}$";
    public static final String USERNAME_MESSAGE =
            "Use 3-30 letters, numbers, dots or underscores, starting with a letter or number";

    /** Names: anything people are actually called (José, O'Brien, Nguyễn-Smith), but no control characters. */
    public static final String NAME_REGEX = "^[^\\p{Cntrl}]*$";
    public static final int NAME_MAX = 50;

    /** Names that would look official, or clash with pages of the site. */
    private static final Set<String> RESERVED = Set.of(
            "admin", "administrator", "root", "system", "support", "help", "fitcheck", "official",
            "staff", "moderator", "api", "login", "logout", "register", "signup", "account", "settings",
            "calendar", "wardrobe", "outfits", "plan", "me", "null", "undefined");

    private ProfileRules() {
    }

    /** " Jessica.M " → "jessica.m": usernames are stored and compared in one form. */
    public static String normalizeUsername(String username) {
        return username == null ? null : username.trim().toLowerCase(Locale.ROOT);
    }

    public static boolean isReserved(String normalizedUsername) {
        return RESERVED.contains(normalizedUsername);
    }

    /** Trims a name; null stays null (validation reports it as missing). */
    public static String cleanName(String name) {
        return name == null ? null : name.trim();
    }
}
