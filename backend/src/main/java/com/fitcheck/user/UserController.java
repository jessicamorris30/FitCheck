package com.fitcheck.user;

import com.fitcheck.auth.AuthenticatedUser;
import com.fitcheck.auth.EmailVerificationService;
import com.fitcheck.config.JwtProperties;
import com.fitcheck.user.dto.DeleteAccountRequest;
import com.fitcheck.user.dto.ProfileRequest;
import com.fitcheck.user.dto.UserResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
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
    private final AccountDeletionService accountDeletion;
    private final JwtProperties jwt;

    public UserController(UserService userService, EmailVerificationService emailVerification,
            AccountDeletionService accountDeletion, JwtProperties jwt) {
        this.userService = userService;
        this.emailVerification = emailVerification;
        this.accountDeletion = accountDeletion;
        this.jwt = jwt;
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
     * PUT /api/users/me/profile {"username","firstName","lastName"} → the updated user.
     * Sets them for accounts from before Phase 7 ("finish your profile") or changes them later.
     * 400 with fieldErrors (fieldErrors.username if it's taken or reserved).
     */
    @PutMapping("/me/profile")
    public UserResponse updateProfile(@AuthenticationPrincipal AuthenticatedUser me,
            @Valid @RequestBody ProfileRequest request) {
        return UserResponse.from(userService.updateProfile(me.id(), request));
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

    /**
     * DELETE /api/users/me {"password": "..."} → 204: the account and everything in it is gone,
     * and the refresh cookie is cleared. 400 with fieldErrors.password if the password is wrong.
     */
    @DeleteMapping("/me")
    public ResponseEntity<Void> deleteAccount(@AuthenticationPrincipal AuthenticatedUser me,
            @Valid @RequestBody DeleteAccountRequest request) {
        accountDeletion.deleteAccount(me.id(), request.password());
        // Tell the browser to drop the refresh cookie (same name/path, empty, Max-Age=0).
        ResponseCookie clear = ResponseCookie.from(jwt.refreshCookieName(), "")
                .httpOnly(true)
                .secure(jwt.refreshCookieSecure())
                .sameSite("Strict")
                .path(jwt.refreshCookiePath())
                .maxAge(0)
                .build();
        return ResponseEntity.noContent().header(HttpHeaders.SET_COOKIE, clear.toString()).build();
    }
}
