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
 * <p>After 5 wrong passwords for one account within 15 minutes, further logins for it are refused
 * until the window ends, even with the right password. A successful login clears the count.
 * The count is per account, whether the email or the username was typed (AuthService picks the
 * key), so switching between them gives no extra guesses.
 *
 * <p>Logins that match no account are counted too (by what was typed), so the lock can't be used
 * to find out who is registered.
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

    /**
     * @param accountKey which account the attempt is for ("user:<id>", or "unknown:<what was typed>")
     * @throws TooManyRequestsException if it is temporarily locked
     */
    public void checkNotLocked(String accountKey) {
        if (!enabled) {
            return;
        }
        RateLimiter.Decision d = limiter.check(key(accountKey), MAX_FAILURES);
        if (!d.allowed()) {
            throw new TooManyRequestsException("Too many failed login attempts for this account. Please wait "
                    + RateLimitFilter.humanize(d.retryAfterSeconds()) + " or reset your password.");
        }
    }

    public void recordFailure(String accountKey) {
        if (enabled) {
            limiter.tryAcquire(key(accountKey), MAX_FAILURES, LOCK_PERIOD);
        }
    }

    public void recordSuccess(String accountKey) {
        limiter.reset(key(accountKey));
    }

    private static String key(String accountKey) {
        return "login-failures:" + accountKey.trim().toLowerCase(Locale.ROOT);
    }
}
