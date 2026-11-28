package com.fitcheck.admin;

import com.fitcheck.auth.AuthenticatedUser;
import com.fitcheck.common.exception.ForbiddenException;
import com.fitcheck.user.User;
import com.fitcheck.user.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpMethod;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * Runs before every /api/admin/** endpoint (see AdminWebConfig).
 *
 * <ol>
 *   <li>Not an admin: answers EXACTLY like a page that doesn't exist (404 "No endpoint ..."), so
 *       nobody can even tell an admin area exists. (Not logged in at all: 401, like the rest of
 *       the API.) Checked against the database every time, so a frozen account or an email
 *       removed from ADMIN_EMAILS loses access immediately.</li>
 *   <li>Admin, asking for the 2-step endpoints themselves (/api/admin/2fa/...): allowed, or the
 *       code could never be entered.</li>
 *   <li>Everything else also needs the "code entered" pass ({@link AdminPassCookie}) for this
 *       user; without it: 403 with {@link #NEEDS_CODE} or {@link #NEEDS_SETUP}.</li>
 * </ol>
 */
@Component
public class AdminGuard implements HandlerInterceptor {

    public static final String NEEDS_SETUP = "Set up two-step verification to use the admin pages.";
    public static final String NEEDS_CODE = "Enter the code from your authenticator app to use the admin pages.";

    private final AdminAccess adminAccess;
    private final UserRepository users;
    private final AdminPassCookie pass;

    public AdminGuard(AdminAccess adminAccess, UserRepository users, AdminPassCookie pass) {
        this.adminAccess = adminAccess;
        this.users = users;
        this.pass = pass;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        User admin = auth != null && auth.getPrincipal() instanceof AuthenticatedUser me
                ? users.findById(me.id()).orElse(null)
                : null;
        if (!adminAccess.isAdmin(admin)) {
            String uri = request.getRequestURI();
            throw new NoResourceFoundException(HttpMethod.valueOf(request.getMethod()), uri,
                    uri.startsWith("/") ? uri.substring(1) : uri);
        }
        if (request.getRequestURI().startsWith("/api/admin/2fa/") || pass.isValidFor(request, admin.getId())) {
            return true;
        }
        throw new ForbiddenException(admin.isTotpEnabled() ? NEEDS_CODE : NEEDS_SETUP);
    }
}
