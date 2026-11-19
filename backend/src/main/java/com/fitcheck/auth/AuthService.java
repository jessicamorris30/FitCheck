package com.fitcheck.auth;

import com.fitcheck.auth.dto.AuthResponse;
import com.fitcheck.auth.dto.LoginRequest;
import com.fitcheck.auth.dto.RegisterRequest;
import com.fitcheck.common.exception.ConflictException;
import com.fitcheck.common.exception.UnauthorizedException;
import com.fitcheck.user.User;
import com.fitcheck.user.UserRepository;
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

    private static final String INVALID_CREDENTIALS = "Invalid email or password";

    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final RefreshTokenService refreshTokens;
    private final EmailVerificationService emailVerification;

    /** A real BCrypt hash of a random string; only used to equalize login timing (see login). */
    private final String dummyHash;

    // Spring sees this constructor and passes in the matching beans ("dependency injection").
    public AuthService(UserRepository users, PasswordEncoder passwordEncoder, JwtService jwtService,
            RefreshTokenService refreshTokens, EmailVerificationService emailVerification) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.refreshTokens = refreshTokens;
        this.emailVerification = emailVerification;
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

        // Never store the password itself: BCrypt turns it into a salted, one-way hash.
        User user;
        try {
            user = users.saveAndFlush(new User(email, passwordEncoder.encode(request.password())));
        } catch (DataIntegrityViolationException e) {
            // Two sign-ups with the same email at the same moment can both pass the check above;
            // the unique index in the database stops the second one and we land here.
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
        User user = users.findByEmailIgnoreCase(normalizeEmail(request.email())).orElse(null);

        if (user == null) {
            // Still run one BCrypt check so "unknown email" takes as long as "wrong password".
            // Otherwise the response time alone would reveal whether an account exists.
            passwordEncoder.matches(request.password(), dummyHash);
            throw new UnauthorizedException(INVALID_CREDENTIALS);
        }
        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new UnauthorizedException(INVALID_CREDENTIALS);
        }
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
