package com.fitcheck.security;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/**
 * Counts events per key ("login:203.0.113.7", "login-failures:ada@example.com") in fixed time
 * windows and says whether one more is allowed.
 *
 * <p>Fixed window = "at most N per period, counting from the first event". Simple, and plenty to
 * stop scripts that try thousands of passwords or sign-ups.
 *
 * <p>Kept in memory: counts reset on restart and aren't shared between several copies of the
 * backend. Fine for one server; if FitCheck is ever scaled out to several, this moves to a shared
 * store such as Redis.
 */
@Component
public class RateLimiter {

    /** Past this many keys, expired windows are swept out so memory can't grow forever. */
    private static final int SWEEP_THRESHOLD = 10_000;

    /** One key's current window: when it ends and how many events it has counted. */
    private record Window(Instant end, int count) {
    }

    /** Whether the event is allowed and, if not, how many seconds until it will be. */
    public record Decision(boolean allowed, long retryAfterSeconds) {
    }

    private final Map<String, Window> windows = new ConcurrentHashMap<>();
    private final Clock clock;

    public RateLimiter(Clock clock) {
        this.clock = clock;
    }

    /** Counts one event for this key, unless the limit for the current window is already reached. */
    public Decision tryAcquire(String key, int limit, Duration period) {
        Instant now = clock.instant();
        sweepIfLarge(now);

        boolean[] allowed = {false};
        // compute() runs atomically per key, so two simultaneous requests can't both slip past the limit.
        Window current = windows.compute(key, (k, w) -> {
            if (w == null || !now.isBefore(w.end())) {
                allowed[0] = true;
                return new Window(now.plus(period), 1); // first event, or the old window is over
            }
            if (w.count() < limit) {
                allowed[0] = true;
                return new Window(w.end(), w.count() + 1);
            }
            return w; // full: refuse, and don't count it
        });
        return allowed[0] ? new Decision(true, 0) : new Decision(false, secondsUntil(current.end(), now));
    }

    /** Looks without counting: is this key currently at its limit? */
    public Decision check(String key, int limit) {
        Instant now = clock.instant();
        Window w = windows.get(key);
        if (w == null || !now.isBefore(w.end()) || w.count() < limit) {
            return new Decision(true, 0);
        }
        return new Decision(false, secondsUntil(w.end(), now));
    }

    /** Forget a key, e.g. someone's failed logins once they log in successfully. */
    public void reset(String key) {
        windows.remove(key);
    }

    private static long secondsUntil(Instant end, Instant now) {
        return Math.max(1, Duration.between(now, end).toSeconds());
    }

    private void sweepIfLarge(Instant now) {
        if (windows.size() > SWEEP_THRESHOLD) {
            windows.entrySet().removeIf(e -> !now.isBefore(e.getValue().end()));
        }
    }
}
