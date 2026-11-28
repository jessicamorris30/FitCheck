package com.fitcheck.admin;

import com.fitcheck.user.UserRepository;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * The ids of frozen accounts, kept in memory so EVERY request can be checked for free.
 *
 * <p>Why: access tokens are checked by signature only (no database), so without this a frozen
 * user would keep working for up to 15 minutes until their token expires. JwtAuthenticationFilter
 * asks {@link #isFrozen} on every request, so freezing takes effect on the very next click.
 *
 * <p>Loaded from the database once at startup (before the server takes requests) and updated
 * when an admin freezes or unfreezes someone. This works because the app runs as a single copy
 * (maxReplicas 1, like the rate limiter); several copies would need a shared store.
 */
@Component
public class FrozenAccounts implements SmartInitializingSingleton {

    private final Set<UUID> frozen = ConcurrentHashMap.newKeySet();
    private final UserRepository users;

    public FrozenAccounts(UserRepository users) {
        this.users = users;
    }

    /** Runs once all beans exist, before the web server starts taking requests. */
    @Override
    public void afterSingletonsInstantiated() {
        frozen.addAll(users.findFrozenIds());
    }

    public boolean isFrozen(UUID userId) {
        return frozen.contains(userId);
    }

    /** Call inside the freezing transaction: applies now, and is undone if that transaction fails. */
    public void markFrozen(UUID userId) {
        boolean added = frozen.add(userId);
        undoIfRolledBack(() -> {
            if (added) {
                frozen.remove(userId);
            }
        });
    }

    /** Call inside the unfreezing transaction: applies now, and is undone if that transaction fails. */
    public void markUnfrozen(UUID userId) {
        boolean removed = frozen.remove(userId);
        undoIfRolledBack(() -> {
            if (removed) {
                frozen.add(userId);
            }
        });
    }

    private static void undoIfRolledBack(Runnable undo) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status != STATUS_COMMITTED) {
                    undo.run();
                }
            }
        });
    }
}
