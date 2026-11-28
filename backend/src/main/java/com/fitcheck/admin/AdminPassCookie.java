package com.fitcheck.admin;

import com.fitcheck.auth.JwtService;
import com.fitcheck.config.JwtProperties;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;
import org.springframework.web.util.WebUtils;

/**
 * The "2-step code entered" pass for admin, as a cookie (Phase 8.2).
 *
 * <ul>
 *   <li>HttpOnly: page scripts can't read it, so a malicious script couldn't steal it.</li>
 *   <li>Path /api/admin: the browser only ever sends it to admin endpoints.</li>
 *   <li>SameSite=Strict + Secure, like the login cookie.</li>
 *   <li>Signed, for one user, {@link #VALID_FOR}; and useless on its own, because every admin
 *       request also needs that same user's normal login token.</li>
 * </ul>
 */
@Component
public class AdminPassCookie {

    public static final String NAME = "fitcheck_admin";
    static final String PATH = "/api/admin";
    public static final Duration VALID_FOR = Duration.ofHours(8);

    private final JwtService jwt;
    private final boolean secure;

    public AdminPassCookie(JwtService jwt, JwtProperties jwtProperties) {
        this.jwt = jwt;
        this.secure = jwtProperties.refreshCookieSecure();
    }

    public ResponseCookie issue(UUID adminId) {
        return build(jwt.createAdmin2faPass(adminId, VALID_FOR), VALID_FOR);
    }

    /** Expires the cookie (logout, 2-step turned off). */
    public ResponseCookie clear() {
        return build("", Duration.ZERO);
    }

    /** True if the request carries a valid, unexpired pass for exactly this user. */
    public boolean isValidFor(HttpServletRequest request, UUID userId) {
        Cookie cookie = WebUtils.getCookie(request, NAME);
        Optional<UUID> owner = cookie == null ? Optional.empty() : jwt.parseAdmin2faPass(cookie.getValue());
        return owner.map(userId::equals).orElse(false);
    }

    private ResponseCookie build(String value, Duration maxAge) {
        return ResponseCookie.from(NAME, value)
                .httpOnly(true)
                .secure(secure)
                .sameSite("Strict")
                .path(PATH)
                .maxAge(maxAge)
                .build();
    }
}
