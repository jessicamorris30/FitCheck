package com.fitcheck.admin;

import com.fitcheck.user.User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Writes the audit log. Every admin action goes through here (step 8.3 onwards). */
@Service
public class AdminAudit {

    private final AdminAuditRepository entries;

    public AdminAudit(AdminAuditRepository entries) {
        this.entries = entries;
    }

    /**
     * Records an action, in the same transaction as the action itself: if the action fails and
     * is rolled back, so is its log line (no "froze X" entry for a freeze that didn't happen).
     *
     * @param action  short code, e.g. "FREEZE", "FORCE_PASSWORD_RESET"
     * @param target  the account acted on, or null for actions not about one account
     * @param details free text (e.g. the freeze reason); never passwords or tokens
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void record(User admin, String action, User target, String details) {
        entries.save(new AdminAuditEntry(admin.getId(), admin.getEmail(), action,
                target == null ? null : target.getId(), target == null ? null : target.getEmail(), details));
    }
}
