package com.fitcheck.auth;

import com.fitcheck.auth.AuthService.AuthSession;
import com.fitcheck.auth.dto.AuthResponse;
import com.fitcheck.auth.dto.ChangePasswordRequest;
import com.fitcheck.auth.dto.ForgotPasswordRequest;
import com.fitcheck.auth.dto.ResetPasswordRequest;
import com.fitcheck.auth.dto.LoginRequest;
import com.fitcheck.auth.dto.RegisterRequest;
import com.fitcheck.auth.dto.UsernameCheckRequest;
import com.fitcheck.auth.dto.UsernameCheckResponse;
import com.fitcheck.auth.dto.VerifyEmailRequest;
import com.fitcheck.common.exception.ApiError;
import com.fitcheck.common.exception.UnauthorizedException;
import com.fitcheck.config.JwtProperties;
import com.fitcheck.user.UserService;
import com.fitcheck.user.dto.UserResponse;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.time.Duration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.WebUtils;

/**
 * HTTP endpoints under /api/auth. These are public (see SecurityConfig), except change-password.
 *
 * <p>Register, login and refresh return the access token in the JSON body and the refresh token
 * in an HttpOnly cookie. The cookie handling lives here because cookies are an HTTP concern.
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;
    private final EmailVerificationService emailVerification;
    private final PasswordResetService passwordReset;
    private final JwtProperties jwt;
    private final UserService userService;

    public AuthController(AuthService authService, EmailVerificationService emailVerification,
            PasswordResetService passwordReset, JwtProperties jwt, UserService userService) {
        this.authService = authService;
        this.emailVerification = emailVerification;
        this.passwordReset = passwordReset;
        this.jwt = jwt;
        this.userService = userService;
    }

    /**
     * POST /api/auth/register → 201 + AuthResponse + refresh cookie.
     * 400 invalid input (fieldErrors.username if the username is taken), 409 email taken.
     */
    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        return withRefreshCookie(HttpStatus.CREATED, authService.register(request));
    }

    /**
     * POST /api/auth/check-username {"username"} → {username, available, message}: the live check
     * while typing a username at sign-up. Always 200; rate-limited per IP.
     */
    @PostMapping("/check-username")
    public UsernameCheckResponse checkUsername(@RequestBody UsernameCheckRequest request) {
        String problem = userService.usernameProblem(request.username(), null);
        return new UsernameCheckResponse(request.username(), problem == null, problem);
    }

    /**
     * POST /api/auth/login {"login": email or username, "password"} → 200 + AuthResponse + refresh
     * cookie. 401 wrong login/password, 429 too many wrong passwords for that account.
     */
    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        return withRefreshCookie(HttpStatus.OK, authService.login(request));
    }

    /**
     * POST /api/auth/refresh (no body; the browser sends the refresh cookie).
     * → 200 + new AuthResponse + rotated cookie, or 401 + cookie cleared.
     */
    @PostMapping("/refresh")
    public ResponseEntity<?> refresh(HttpServletRequest request) {
        String token = readRefreshCookie(request);
        if (token == null) {
            return unauthorized("No session");
        }
        try {
            return withRefreshCookie(HttpStatus.OK, authService.refresh(token));
        } catch (UnauthorizedException e) {
            return unauthorized(e.getMessage());
        }
    }

    /**
     * POST /api/auth/verify-email {"token": "..."} → 200 + the now-verified user, or 400 if the
     * link is invalid or expired. Public: the user may open the link on a device where they
     * aren't logged in. The token itself proves they received the email.
     */
    @PostMapping("/verify-email")
    public UserResponse verifyEmail(@Valid @RequestBody VerifyEmailRequest request) {
        return UserResponse.from(emailVerification.verify(request.token()));
    }

    /**
     * POST /api/auth/change-password {"currentPassword", "newPassword"} (login required)
     * → 200 + a new AuthResponse + a new refresh cookie for THIS device; every other device is signed out.
     * 400 with fieldErrors if the current password is wrong or the new one is invalid or unchanged.
     */
    @PostMapping("/change-password")
    public ResponseEntity<AuthResponse> changePassword(@AuthenticationPrincipal AuthenticatedUser me,
            @Valid @RequestBody ChangePasswordRequest request) {
        return withRefreshCookie(HttpStatus.OK,
                authService.changePassword(me.id(), request.currentPassword(), request.newPassword()));
    }

    /**
     * POST /api/auth/forgot-password {"email"} → always 204, whether or not the email has an
     * account (so this can't be used to discover who's registered). If it does, a reset link is emailed.
     */
    @PostMapping("/forgot-password")
    public ResponseEntity<Void> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        passwordReset.requestReset(request.email());
        return ResponseEntity.noContent().build();
    }

    /**
     * POST /api/auth/reset-password {"token", "newPassword"} → 204; the user then logs in with the
     * new password. 400 if the link is invalid, used or expired, or the password is invalid.
     */
    @PostMapping("/reset-password")
    public ResponseEntity<Void> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        passwordReset.resetPassword(request.token(), request.newPassword());
        return ResponseEntity.noContent().build();
    }

    /** POST /api/auth/logout → 204, session revoked and cookie cleared. Safe to call twice. */
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletRequest request) {
        String token = readRefreshCookie(request);
        if (token != null) {
            authService.logout(token);
        }
        return ResponseEntity.noContent().header(HttpHeaders.SET_COOKIE, clearCookie().toString()).build();
    }

    // ---- cookie helpers ----

    private ResponseEntity<AuthResponse> withRefreshCookie(HttpStatus status, AuthSession session) {
        return ResponseEntity.status(status)
                .header(HttpHeaders.SET_COOKIE, refreshCookie(session.refreshToken(), jwt.refreshTokenTtl()).toString())
                .body(session.body());
    }

    private ResponseEntity<ApiError> unauthorized(String message) {
        HttpStatus status = HttpStatus.UNAUTHORIZED;
        return ResponseEntity.status(status)
                .header(HttpHeaders.SET_COOKIE, clearCookie().toString())
                .body(ApiError.of(status.value(), status.getReasonPhrase(), message));
    }

    /**
     * HttpOnly: JavaScript can't read it. Secure: HTTPS only (off in dev). SameSite=Strict: never
     * sent on requests started by other sites. Path: only sent to /api/auth/*, not every API call.
     */
    private ResponseCookie refreshCookie(String value, Duration maxAge) {
        return ResponseCookie.from(jwt.refreshCookieName(), value)
                .httpOnly(true)
                .secure(jwt.refreshCookieSecure())
                .sameSite("Strict")
                .path(jwt.refreshCookiePath())
                .maxAge(maxAge)
                .build();
    }

    /** Same cookie with an empty value and Max-Age=0, which tells the browser to delete it. */
    private ResponseCookie clearCookie() {
        return refreshCookie("", Duration.ZERO);
    }

    private String readRefreshCookie(HttpServletRequest request) {
        Cookie cookie = WebUtils.getCookie(request, jwt.refreshCookieName());
        return cookie == null || cookie.getValue().isBlank() ? null : cookie.getValue();
    }
}
