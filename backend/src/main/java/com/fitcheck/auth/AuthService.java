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
 * Account sign-up and login.
 *
 * <p>Services hold the business rules. The controller only translates HTTP to and from Java;
 * the repository only talks to the database.
 */
@Service
public class AuthService {

    private static final String INVALID_CREDENTIALS = "Invalid email or password";

    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    /** A real BCrypt hash of a random string; only used to equalize login timing (see login). */
    private final String dummyHash;

    // Spring sees this constructor and passes in the matching beans ("dependency injection").
    public AuthService(UserRepository users, PasswordEncoder passwordEncoder, JwtService jwtService) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.dummyHash = passwordEncoder.encode(UUID.randomUUID().toString());
    }

    /**
     * Creates a new account.
     *
     * @throws ConflictException if the email is already registered (in any letter case)
     */
    @Transactional
    public User register(RegisterRequest request) {
        String email = normalizeEmail(request.email());

        if (users.existsByEmailIgnoreCase(email)) {
            throw new ConflictException("An account with this email already exists");
        }

        // Never store the password itself: BCrypt turns it into a salted, one-way hash.
        User user = new User(email, passwordEncoder.encode(request.password()));
        try {
            return users.saveAndFlush(user);
        } catch (DataIntegrityViolationException e) {
            // Two sign-ups with the same email at the same moment can both pass the check above;
            // the unique index in the database stops the second one and we land here.
            throw new ConflictException("An account with this email already exists");
        }
    }

    /**
     * Checks email + password and, if they match, issues an access token.
     *
     * @throws UnauthorizedException with the same message whether the email is unknown or the
     *         password is wrong, so attackers can't use login to discover which emails have accounts
     */
    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request) {
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

        String accessToken = jwtService.createAccessToken(user.getId(), user.getEmail());
        return new AuthResponse(accessToken, jwtService.accessTokenTtlSeconds(), UserResponse.from(user));
    }

    /** " Ada@Example.COM " → "ada@example.com", so each address is stored one way. */
    static String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
