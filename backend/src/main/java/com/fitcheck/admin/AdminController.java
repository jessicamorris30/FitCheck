package com.fitcheck.admin;

import com.fitcheck.auth.AuthenticatedUser;
import java.util.Map;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Admin endpoints under /api/admin. Every request passes {@link AdminGuard} first, so the methods
 * here can assume the caller is an admin. (User management and monitoring come in 8.3 and 8.5.)
 */
@RestController
@RequestMapping("/api/admin")
public class AdminController {

    /** GET /api/admin/me → who you are as admin (lets the admin pages check access). */
    @GetMapping("/me")
    public Map<String, Object> me(@AuthenticationPrincipal AuthenticatedUser me) {
        return Map.of("id", me.id(), "email", me.email(), "admin", true);
    }
}
