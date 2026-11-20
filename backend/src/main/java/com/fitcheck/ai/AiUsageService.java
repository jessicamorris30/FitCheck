package com.fitcheck.ai;

import com.fitcheck.common.exception.ForbiddenException;
import com.fitcheck.common.exception.TooManyRequestsException;
import com.fitcheck.config.AiProperties;
import com.fitcheck.user.User;
import com.fitcheck.user.UserService;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The AI stylist's guard rails: who may use it, and how often.
 *
 * <ul>
 *   <li><b>Confirmed email required.</b> Stops throwaway sign-ups with fake addresses from
 *       spending your API credit.
 *   <li><b>Daily limit</b> ({@code fitcheck.ai.daily-limit}, 20) per user. "Today" means the UTC
 *       day, so everyone's counter resets at the same moment (midnight UTC).
 * </ul>
 */
@Service
public class AiUsageService {

    private final AiUsageRepository usage;
    private final UserService users;
    private final AiProperties properties;
    private final Clock clock;

    public AiUsageService(AiUsageRepository usage, UserService users, AiProperties properties, Clock clock) {
        this.usage = usage;
        this.users = users;
        this.properties = properties;
        this.clock = clock;
    }

    /** What the UI shows: "n of 20 left today, resets at ...". */
    public record UsageSummary(long used, int limit, long remaining, Instant resetsAt) {
    }

    @Transactional(readOnly = true)
    public UsageSummary summary(UUID userId) {
        long used = usage.countByUser_IdAndCreatedAtGreaterThanEqual(userId, startOfToday());
        int limit = properties.dailyLimit();
        return new UsageSummary(used, limit, Math.max(0, limit - used), startOfToday().plusSeconds(24 * 60 * 60));
    }

    /**
     * Checks the rules and, if allowed, records one request. Called right before Claude is asked,
     * after all free checks (prompt, photo, wardrobe) have passed, so a bad request costs no credit.
     *
     * @return the id of the usage row, so it can be refunded if the AI then fails on our side
     * @throws ForbiddenException       if the email isn't confirmed yet
     * @throws TooManyRequestsException if today's limit is used up
     */
    @Transactional
    public UUID checkAndRecord(UUID userId) {
        User user = users.getById(userId);
        if (!user.isEmailVerified()) {
            throw new ForbiddenException("Please confirm your email to use the AI stylist.");
        }
        long used = usage.countByUser_IdAndCreatedAtGreaterThanEqual(userId, startOfToday());
        if (used >= properties.dailyLimit()) {
            throw new TooManyRequestsException(
                    "You've used all " + properties.dailyLimit() + " AI suggestions for today. Try again tomorrow.");
        }
        return usage.save(new AiUsage(user, clock.instant())).getId();
    }

    /**
     * Gives a request back when the AI was unavailable (outage, unusable answer): the user
     * shouldn't lose a try because of a problem on our side. Refusals are NOT refunded:
     * Claude did the work.
     */
    @Transactional
    public void refund(UUID usageId) {
        usage.deleteById(usageId);
    }

    private Instant startOfToday() {
        return LocalDate.now(clock.withZone(ZoneOffset.UTC)).atStartOfDay(ZoneOffset.UTC).toInstant();
    }
}
