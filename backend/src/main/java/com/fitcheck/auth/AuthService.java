package com.fitcheck.auth;

import com.fitcheck.auth.dto.RegisterRequest;
import com.fitcheck.common.exception.ConflictException;
import com.fitcheck.user.User;
import com.fitcheck.user.UserRepository;
import java.util.Locale;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Account sign-up (and, in step 4, login).
 *
 * <p>Services hold the business rules. The controller only translates HTTP to and from Java;
 * the repository only talks to the database.
 */
@Service
public class AuthService {

    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;

    // Spring sees this constructor and passes in the matching beans ("dependency injection").
    public AuthService(UserRepository users, PasswordEncoder passwordEncoder) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
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

    /** " Ada@Example.COM " → "ada@example.com", so each address is stored one way. */
    static String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
