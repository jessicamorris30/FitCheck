package com.fitcheck.security;

import com.fitcheck.common.exception.TooManyRequestsException;
import java.time.Duration;
import java.util.Locale;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Protects each ACCOUNT against password guessing, on top of the per-IP limit in RateLimitFilter
 * (an attacker with many IP addresses gets past per-IP limits, but not this).
 *
 * <p>After 5 wrong passwords for one email within 15 minutes, further logins for that email are
 * refused until the window ends, even with the right password. A successful login clears the count.
 *
 * <p>Counted for every email, whether or not it has an account, so the lock can't be used to find
 * out who is registered.
 */
@Service
public class LoginAttemptService {

    static final int MAX_FAILURES = 5;
    static final Duration LOCK_PERIOD = Duration.ofMinutes(15);

    private final RateLimiter limiter;
    private final boolean enabled;

    public LoginAttemptService(RateLimiter limiter, @Value("${fitcheck.rate-limit.enabled:true}") boolean enabled) {
        this.limiter = limiter;
        this.enabled = enabled;
    }

    /** @throws TooManyRequestsException if this email is temporarily locked */
    public void checkNotLocked(String email) {
        if (!enabled) {
            return;
        }
        RateLimiter.Decision d = limiter.check(key(email), MAX_FAILURES);
        if (!d.allowed()) {
            throw new TooManyRequestsException("Too many failed login attempts for this account. Please wait "
                    + RateLimitFilter.humanize(d.retryAfterSeconds()) + " or reset your password.");
        }
    }

    public void recordFailure(String email) {
        if (enabled) {
            limiter.tryAcquire(key(email), MAX_FAILURES, LOCK_PERIOD);
        }
    }

    public void recordSuccess(String email) {
        limiter.reset(key(email));
    }

    private static String key(String email) {
        return "login-failures:" + email.trim().toLowerCase(Locale.ROOT);
    }
}
