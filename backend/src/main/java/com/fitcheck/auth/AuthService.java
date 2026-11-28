package com.fitcheck.auth;

import com.fitcheck.auth.dto.AuthResponse;
import com.fitcheck.auth.dto.LoginRequest;
import com.fitcheck.auth.dto.RegisterRequest;
import com.fitcheck.common.exception.ConflictException;
import com.fitcheck.common.exception.FieldErrorException;
import com.fitcheck.security.LoginAttemptService;
import com.fitcheck.common.exception.UnauthorizedException;
import com.fitcheck.user.ProfileRules;
import com.fitcheck.user.User;
import com.fitcheck.user.UserRepository;
import com.fitcheck.user.UserService;
import com.fitcheck.user.dto.UserResponse;
import java.util.Locale;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Sign-up, login, refresh and logout.
 *
 * <p>Services hold the business rules. The controller only translates HTTP to and from Java
 * (including cookies); the repositories only talk to the database.
 */
@Service
public class AuthService {

    private static final String INVALID_CREDENTIALS = "Invalid email, username or password";

    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final RefreshTokenService refreshTokens;
    private final EmailVerificationService emailVerification;
    private final LoginAttemptService loginAttempts;
    private final UserService userService;

    /** A real BCrypt hash of a random string; only used to equalize login timing (see login). */
    private final String dummyHash;

    // Spring sees this constructor and passes in the matching beans ("dependency injection").
    public AuthService(UserRepository users, PasswordEncoder passwordEncoder, JwtService jwtService,
            RefreshTokenService refreshTokens, EmailVerificationService emailVerification,
            LoginAttemptService loginAttempts, UserService userService) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.refreshTokens = refreshTokens;
        this.emailVerification = emailVerification;
        this.loginAttempts = loginAttempts;
        this.userService = userService;
        this.dummyHash = passwordEncoder.encode(UUID.randomUUID().toString());
    }

    /**
     * The result of logging someone in: the JSON body for the client, plus the refresh token
     * the controller will put in the HttpOnly cookie (it never goes in the body).
     */
    public record AuthSession(AuthResponse body, String refreshToken) {
    }

    /**
     * Creates a new account and logs it in straight away.
     *
     * @throws ConflictException if the email is already registered (in any letter case)
     */
    @Transactional
    public AuthSession register(RegisterRequest request) {
        String email = normalizeEmail(request.email());

        if (users.existsByEmailIgnoreCase(email)) {
            throw new ConflictException("An account with this email already exists");
        }
        String usernameProblem = userService.usernameProblem(request.username(), null);
        if (usernameProblem != null) {
            throw new FieldErrorException("username", usernameProblem);
        }

        // Never store the password itself: BCrypt turns it into a salted, one-way hash.
        User user = new User(email, passwordEncoder.encode(request.password()));
        user.setProfile(request.username(), request.firstName(), request.lastName());
        try {
            user = users.saveAndFlush(user);
        } catch (DataIntegrityViolationException e) {
            // Two sign-ups with the same email or username at the same moment can both pass the
            // checks above; the unique indexes in the database stop the second one and we land here.
            if (users.existsByUsernameIgnoreCase(request.username())) {
                throw new FieldErrorException("username", "That username is already taken");
            }
            throw new ConflictException("An account with this email already exists");
        }
        // Queues the "Confirm your email" message; it is sent after this transaction commits.
        emailVerification.sendVerificationEmail(user);
        return startSession(user);
    }

    /**
     * Checks email + password and, if they match, logs the user in.
     *
     * @throws UnauthorizedException with the same message whether the email is unknown or the
     *         password is wrong, so attackers can't use login to discover which emails have accounts
     */
    @Transactional
    public AuthSession login(LoginRequest request) {
        // Anything with '@' is an email; usernames can't contain one (ProfileRules).
        String typed = request.login();
        User user = typed.contains("@")
                ? users.findByEmailIgnoreCase(normalizeEmail(typed)).orElse(null)
                : users.findByUsernameIgnoreCase(ProfileRules.normalizeUsername(typed)).orElse(null);

        // The "too many wrong passwords" lock belongs to the ACCOUNT, so typing the email and then
        // the username can't buy extra guesses. Unknown logins get their own counter.
        String lockKey = user != null ? "user:" + user.getId() : "unknown:" + typed.toLowerCase(Locale.ROOT);
        loginAttempts.checkNotLocked(lockKey); // too many wrong passwords lately → 429

        if (user == null) {
            // Still run one BCrypt check so "unknown account" takes as long as "wrong password".
            // Otherwise the response time alone would reveal whether an account exists.
            passwordEncoder.matches(request.password(), dummyHash);
            loginAttempts.recordFailure(lockKey);
            throw new UnauthorizedException(INVALID_CREDENTIALS);
        }
        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            loginAttempts.recordFailure(lockKey);
            throw new UnauthorizedException(INVALID_CREDENTIALS);
        }
        loginAttempts.recordSuccess(lockKey);
        return startSession(user);
    }

    /**
     * Swaps a refresh token for a new access token and a new (rotated) refresh token.
     *
     * @throws UnauthorizedException if the refresh token is unknown, expired or reused
     */
    @Transactional(noRollbackFor = UnauthorizedException.class)
    public AuthSession refresh(String refreshToken) {
        RefreshTokenService.IssuedToken next = refreshTokens.rotate(refreshToken);
        return new AuthSession(accessFor(next.user()), next.value());
    }

    /**
     * Changes the password of the logged-in user.
     *
     * <p>Afterwards every existing session is signed out, on every device: if someone else knew
     * the old password and was logged in, they're out. This device gets a brand-new session, so
     * the user who just changed their password stays logged in here.
     *
     * <p>Access tokens already handed out elsewhere can't be "recalled" (they're checked by
     * signature only), but they expire within 15 minutes and can't be refreshed anymore.
     *
     * @throws FieldErrorException if the current password is wrong, or the new one is the same
     */
    @Transactional
    public AuthSession changePassword(UUID userId, String currentPassword, String newPassword) {
        User user = users.findById(userId).orElseThrow(() -> new UnauthorizedException("Please log in again"));

        if (!passwordEncoder.matches(currentPassword, user.getPasswordHash())) {
            throw new FieldErrorException("currentPassword", "Current password is incorrect");
        }
        if (passwordEncoder.matches(newPassword, user.getPasswordHash())) {
            throw new FieldErrorException("newPassword", "Choose a password different from your current one");
        }

        user.setPasswordHash(passwordEncoder.encode(newPassword));
        refreshTokens.revokeAll(userId);
        return startSession(user);
    }

    /** Ends the session this refresh token belongs to. */
    @Transactional
    public void logout(String refreshToken) {
        refreshTokens.revoke(refreshToken);
    }

    private AuthSession startSession(User user) {
        RefreshTokenService.IssuedToken refresh = refreshTokens.issue(user);
        return new AuthSession(accessFor(user), refresh.value());
    }

    private AuthResponse accessFor(User user) {
        String accessToken = jwtService.createAccessToken(user.getId(), user.getEmail());
        return new AuthResponse(accessToken, jwtService.accessTokenTtlSeconds(), UserResponse.from(user));
    }

    /** " Ada@Example.COM " → "ada@example.com", so each address is stored one way. */
    static String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
