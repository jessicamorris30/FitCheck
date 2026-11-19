package com.fitcheck.user;

import com.fitcheck.auth.AuthenticatedUser;
import com.fitcheck.user.dto.UserResponse;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Endpoints about the logged-in user. All require a valid access token (see SecurityConfig).
 */
@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    /**
     * GET /api/users/me: who am I?
     *
     * <p>{@code @AuthenticationPrincipal} hands us the user that JwtAuthenticationFilter found in
     * the token. We never take a user id from the URL or body for "my" data; it always comes from
     * the token, so nobody can ask for someone else's account.
     */
    @GetMapping("/me")
    public UserResponse me(@AuthenticationPrincipal AuthenticatedUser me) {
        return UserResponse.from(userService.getById(me.id()));
    }
}
