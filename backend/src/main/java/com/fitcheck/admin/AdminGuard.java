package com.fitcheck.admin;

import com.fitcheck.auth.AuthenticatedUser;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpMethod;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * Runs before every /api/admin/** endpoint (see AdminWebConfig). Lets admins through; for
 * everyone else it answers EXACTLY like a page that doesn't exist (404 "No endpoint ..."), so
 * nobody can even tell that an admin area exists. (Not logged in at all → 401, like the rest of
 * the API.)
 *
 * <p>The admin check reads the account fresh from the database on every request
 * ({@link AdminAccess#isAdmin(java.util.UUID)}), so a frozen account or an email removed from
 * the ADMIN_EMAILS setting loses access immediately, not when its token expires.
 */
@Component
public class AdminGuard implements HandlerInterceptor {

    private final AdminAccess adminAccess;

    public AdminGuard(AdminAccess adminAccess) {
        this.adminAccess = adminAccess;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof AuthenticatedUser me && adminAccess.isAdmin(me.id())) {
            return true;
        }
        String uri = request.getRequestURI();
        throw new NoResourceFoundException(HttpMethod.valueOf(request.getMethod()), uri,
                uri.startsWith("/") ? uri.substring(1) : uri);
    }
}
