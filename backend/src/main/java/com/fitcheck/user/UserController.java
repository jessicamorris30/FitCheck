package com.fitcheck.user;

import com.fitcheck.auth.AuthenticatedUser;
import com.fitcheck.auth.EmailVerificationService;
import com.fitcheck.user.dto.UserResponse;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Endpoints about the logged-in user. All require a valid access token (see SecurityConfig).
 */
@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserService userService;
    private final EmailVerificationService emailVerification;

    public UserController(UserService userService, EmailVerificationService emailVerification) {
        this.userService = userService;
        this.emailVerification = emailVerification;
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

    /**
     * POST /api/users/me/verification-email: send the confirmation email again.
     * 204 sent, 409 already verified, 429 asked again too soon.
     */
    @PostMapping("/me/verification-email")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void resendVerificationEmail(@AuthenticationPrincipal AuthenticatedUser me) {
        emailVerification.resend(me.id());
    }
}
