package com.fitcheck.admin;

import com.fitcheck.auth.SecureTokens;
import com.fitcheck.common.exception.BadRequestException;
import com.fitcheck.common.exception.ConflictException;
import com.fitcheck.common.exception.FieldErrorException;
import com.fitcheck.common.exception.NotFoundException;
import com.fitcheck.common.exception.TooManyRequestsException;
import com.fitcheck.security.RateLimitFilter;
import com.fitcheck.security.RateLimiter;
import com.fitcheck.user.User;
import com.fitcheck.user.UserRepository;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 2-step login for admin (Phase 8.2): setting it up, checking codes, backup codes.
 *
 * <p>Guessing is limited: {@link #MAX_WRONG_CODES} wrong codes per {@link #LOCK_PERIOD} lock code
 * entry for that account (a 6-digit code has a million possibilities; 5 tries per 15 minutes
 * makes guessing hopeless).
 */
@Service
public class AdminTwoFactorService {

    static final int MAX_WRONG_CODES = 5;
    static final Duration LOCK_PERIOD = Duration.ofMinutes(15);
    static final int RECOVERY_CODES = 10;
    private static final String RECOVERY_ALPHABET = "ABCDEFGHJKMNPQRSTUVWXYZ23456789"; // no 0/O, 1/I/L
    private static final SecureRandom RANDOM = new SecureRandom();
    static final String WRONG_CODE = "That code didn't work. Check your authenticator app and try again.";

    private final UserRepository users;
    private final RecoveryCodeRepository recoveryCodes;
    private final RateLimiter limiter;
    private final boolean limitsEnabled;
    private final AdminAudit audit;
    private final AdminStepUp stepUp;
    private final Clock clock;

    public AdminTwoFactorService(UserRepository users, RecoveryCodeRepository recoveryCodes, RateLimiter limiter,
            @Value("${fitcheck.rate-limit.enabled:true}") boolean limitsEnabled, AdminAudit audit, AdminStepUp stepUp,
            Clock clock) {
        this.users = users;
        this.recoveryCodes = recoveryCodes;
        this.limiter = limiter;
        this.limitsEnabled = limitsEnabled;
        this.audit = audit;
        this.stepUp = stepUp;
        this.clock = clock;
    }

    /** What the setup screen needs: the secret (to type in by hand) and the QR code's content. */
    public record Setup(String secret, String otpauthUri) {
    }

    public record Status(boolean enabled, long recoveryCodesLeft) {
    }

    @Transactional(readOnly = true)
    public Status status(UUID adminId) {
        User admin = user(adminId);
        return new Status(admin.isTotpEnabled(),
                admin.isTotpEnabled() ? recoveryCodes.countByUser_IdAndUsedAtIsNull(adminId) : 0);
    }

    /**
     * Step 1 of setup: a fresh secret to scan. Nothing is switched on until {@link #enable}.
     *
     * @throws ConflictException if 2-step is already on (turn it off first)
     */
    @Transactional
    public Setup startSetup(UUID adminId) {
        User admin = user(adminId);
        if (admin.isTotpEnabled()) {
            throw new ConflictException("Two-step verification is already on.");
        }
        String secret = Totp.newSecret();
        admin.startTotpSetup(secret);
        return new Setup(secret, Totp.otpauthUri(secret, admin.getEmail()));
    }

    /**
     * Step 2 of setup: a working code from the app switches 2-step on.
     *
     * @return the backup codes, shown ONCE (only their hashes are stored)
     */
    @Transactional(noRollbackFor = {FieldErrorException.class, TooManyRequestsException.class})
    public List<String> enable(UUID adminId, String code) {
        User admin = user(adminId);
        if (admin.isTotpEnabled()) {
            throw new ConflictException("Two-step verification is already on.");
        }
        if (admin.getTotpPendingSecret() == null) {
            throw new BadRequestException("Start the setup first.");
        }
        checkNotLocked(adminId);
        long step = Totp.matchingStep(admin.getTotpPendingSecret(), code, currentStep());
        if (step < 0) {
            wrongCode(adminId);
        }
        admin.enableTotp(clock.instant(), step);
        limiter.reset(lockKey(adminId));
        audit.record(admin, "2FA_ENABLED", admin, null);
        return newRecoveryCodes(admin);
    }

    /**
     * Checks a code from the app, or a backup code. Used to unlock admin for a while, and before
     * turning 2-step off or making new backup codes.
     *
     * @throws FieldErrorException       (fieldErrors.code) for a wrong, reused or used-up code
     * @throws TooManyRequestsException after {@link #MAX_WRONG_CODES} wrong codes
     */
    @Transactional(noRollbackFor = {FieldErrorException.class, TooManyRequestsException.class})
    public void verify(UUID adminId, String code) {
        User admin = user(adminId);
        if (!admin.isTotpEnabled()) {
            throw new BadRequestException("Two-step verification isn't set up yet.");
        }
        checkNotLocked(adminId);

        long step = Totp.matchingStep(admin.getTotpSecret(), code, currentStep());
        if (step >= 0) {
            if (step <= admin.getTotpLastStep()) {
                // Already used (or an older one): someone may have seen it. Wait for the next one.
                wrongCodeWith(adminId, "That code was already used. Wait for the next one in your app.");
            }
            admin.setTotpLastStep(step);
            limiter.reset(lockKey(adminId));
            return;
        }

        String normalized = code == null ? "" : code.replaceAll("[\\s-]", "").toUpperCase(Locale.ROOT);
        if (normalized.length() == 10) {
            var backup = recoveryCodes.findByUser_IdAndCodeHashAndUsedAtIsNull(adminId, SecureTokens.sha256(normalized));
            if (backup.isPresent()) {
                backup.get().markUsed(clock.instant());
                limiter.reset(lockKey(adminId));
                audit.record(admin, "2FA_BACKUP_CODE_USED", admin,
                        recoveryCodes.countByUser_IdAndUsedAtIsNull(adminId) - 1 + " backup codes left");
                return;
            }
        }
        wrongCode(adminId);
    }

    /** Turns 2-step off (e.g. new phone): needs the password AND a current code or backup code. */
    @Transactional(noRollbackFor = {FieldErrorException.class, TooManyRequestsException.class})
    public void disable(UUID adminId, String password, String code) {
        User admin = user(adminId);
        stepUp.requirePassword(admin, password);
        verify(adminId, code);
        admin.disableTotp();
        recoveryCodes.deleteAllForUser(adminId);
        audit.record(admin, "2FA_DISABLED", admin, null);
    }

    /** New backup codes (the old ones stop working); needs a current code. */
    @Transactional(noRollbackFor = {FieldErrorException.class, TooManyRequestsException.class})
    public List<String> regenerateRecoveryCodes(UUID adminId, String code) {
        verify(adminId, code);
        User admin = user(adminId);
        recoveryCodes.deleteAllForUser(adminId);
        audit.record(admin, "2FA_BACKUP_CODES_RENEWED", admin, null);
        return newRecoveryCodes(admin);
    }

    // ---- helpers ----

    private List<String> newRecoveryCodes(User admin) {
        List<String> shown = new ArrayList<>();
        for (int i = 0; i < RECOVERY_CODES; i++) {
            StringBuilder raw = new StringBuilder();
            for (int c = 0; c < 10; c++) {
                raw.append(RECOVERY_ALPHABET.charAt(RANDOM.nextInt(RECOVERY_ALPHABET.length())));
            }
            recoveryCodes.save(new RecoveryCode(admin, SecureTokens.sha256(raw.toString())));
            shown.add(raw.substring(0, 5) + "-" + raw.substring(5)); // "ABCDE-FGHJK"
        }
        return shown;
    }

    private long currentStep() {
        return Totp.stepAt(clock.instant().getEpochSecond());
    }

    private User user(UUID id) {
        return users.findById(id).orElseThrow(() -> new NotFoundException("User not found"));
    }

    private static String lockKey(UUID adminId) {
        return "admin-2fa:" + adminId;
    }

    private void checkNotLocked(UUID adminId) {
        if (!limitsEnabled) {
            return;
        }
        RateLimiter.Decision d = limiter.check(lockKey(adminId), MAX_WRONG_CODES);
        if (!d.allowed()) {
            throw new TooManyRequestsException("Too many wrong codes. Please wait "
                    + RateLimitFilter.humanize(d.retryAfterSeconds()) + " and try again.");
        }
    }

    private void wrongCode(UUID adminId) {
        wrongCodeWith(adminId, WRONG_CODE);
    }

    private void wrongCodeWith(UUID adminId, String message) {
        if (limitsEnabled) {
            limiter.tryAcquire(lockKey(adminId), MAX_WRONG_CODES, LOCK_PERIOD);
        }
        throw new FieldErrorException("code", message);
    }
}
