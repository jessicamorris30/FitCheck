package com.fitcheck.admin;

import com.fitcheck.user.UserRepository;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
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
 * <p>Loaded from the database on first use (the first request after a start), not while the app
 * starts: the Docker build starts the app once WITHOUT a database to record its startup cache
 * (see the Dockerfile), and loading at startup broke that build. Then kept up to date when an
 * admin freezes or unfreezes someone. This works because the app runs as a single copy
 * (maxReplicas 1, like the rate limiter); several copies would need a shared store.
 */
@Component
public class FrozenAccounts {

    private final Set<UUID> frozen = ConcurrentHashMap.newKeySet();
    private final UserRepository users;
    private volatile boolean loaded;

    public FrozenAccounts(UserRepository users) {
        this.users = users;
    }

    public boolean isFrozen(UUID userId) {
        ensureLoaded();
        return frozen.contains(userId);
    }

    /** Call inside the freezing transaction: applies now, and is undone if that transaction fails. */
    public void markFrozen(UUID userId) {
        ensureLoaded();
        boolean added = frozen.add(userId);
        undoIfRolledBack(() -> {
            if (added) {
                frozen.remove(userId);
            }
        });
    }

    /** Call inside the unfreezing transaction: applies now, and is undone if that transaction fails. */
    public void markUnfrozen(UUID userId) {
        ensureLoaded();
        boolean removed = frozen.remove(userId);
        undoIfRolledBack(() -> {
            if (removed) {
                frozen.add(userId);
            }
        });
    }

    /** Reads the frozen accounts once; requests arriving at the same moment wait for it. */
    private void ensureLoaded() {
        if (loaded) {
            return;
        }
        synchronized (this) {
            if (!loaded) {
                frozen.addAll(users.findFrozenIds());
                loaded = true;
            }
        }
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
