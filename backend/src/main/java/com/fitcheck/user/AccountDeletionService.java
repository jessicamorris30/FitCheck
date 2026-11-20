package com.fitcheck.user;

import com.fitcheck.common.exception.FieldErrorException;
import com.fitcheck.storage.BlobStorageService;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Permanently deletes an account and everything in it.
 *
 * <p>What goes:
 * <ul>
 *   <li><b>Database:</b> deleting the users row is enough. Every table that belongs to a user
 *       (refresh tokens, confirmation and reset links, clothing items, outfits and their items,
 *       AI usage) has {@code ON DELETE CASCADE}, so Postgres removes them in the same transaction.
 *   <li><b>Blob Storage:</b> every file under {@code users/<id>/} (photos, thumbnails, and any
 *       leftovers), deleted only AFTER the database commit. If the database part fails, nothing
 *       is lost; if the file part fails, it's logged and the files are orphans no one can reach.
 * </ul>
 */
@Service
public class AccountDeletionService {

    private static final Logger log = LoggerFactory.getLogger(AccountDeletionService.class);

    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final BlobStorageService storage;

    public AccountDeletionService(UserRepository users, PasswordEncoder passwordEncoder, BlobStorageService storage) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.storage = storage;
    }

    /**
     * @param password the account's current password: asked again because this can't be undone,
     *                 and so that someone at an unlocked computer can't delete your account
     * @throws FieldErrorException if the password is wrong
     */
    @Transactional
    public void deleteAccount(UUID userId, String password) {
        User user = users.findById(userId).orElseThrow(() -> new FieldErrorException("password", "Account not found"));
        if (!passwordEncoder.matches(password, user.getPasswordHash())) {
            throw new FieldErrorException("password", "Password is incorrect");
        }

        users.delete(user);
        users.flush(); // run the DELETE (and its cascades) now, so any problem surfaces before the files go

        String prefix = "users/" + userId + "/";
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                try {
                    int removed = storage.deleteAllWithPrefix(prefix);
                    log.info("Deleted account {} and {} stored file(s)", userId, removed);
                } catch (RuntimeException e) {
                    log.error("Account {} deleted but its files under {} could not all be removed", userId, prefix, e);
                }
            }
        });
    }
}
