package com.fitcheck.admin;

import com.fitcheck.common.exception.FieldErrorException;
import com.fitcheck.security.LoginAttemptService;
import com.fitcheck.user.User;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * "Type your password again" before an admin does something drastic (freeze, force a password
 * reset, delete an account; step 8.3) or turns 2-step login off. Wrong guesses count towards the
 * same per-account lock as logins, so this can't be used to guess the password either.
 */
@Component
public class AdminStepUp {

    private final PasswordEncoder passwordEncoder;
    private final LoginAttemptService attempts;

    public AdminStepUp(PasswordEncoder passwordEncoder, LoginAttemptService attempts) {
        this.passwordEncoder = passwordEncoder;
        this.attempts = attempts;
    }

    /** @throws FieldErrorException (400, fieldErrors.password) if it isn't the admin's password */
    public void requirePassword(User admin, String password) {
        String lockKey = "user:" + admin.getId();
        attempts.checkNotLocked(lockKey);
        if (password == null || !passwordEncoder.matches(password, admin.getPasswordHash())) {
            attempts.recordFailure(lockKey);
            throw new FieldErrorException("password", "Incorrect password");
        }
        attempts.recordSuccess(lockKey);
    }
}
