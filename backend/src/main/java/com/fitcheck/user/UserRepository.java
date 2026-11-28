package com.fitcheck.user;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Database access for {@link User}.
 *
 * <p>Spring Data writes the implementation at startup. JpaRepository already gives us
 * save, findById, findAll, delete, and more. The methods below are "derived queries":
 * Spring reads the method name and generates the SQL, e.g.
 * {@code findByEmailIgnoreCase} becomes {@code ... WHERE upper(email) = upper(?)}.
 */
public interface UserRepository extends JpaRepository<User, UUID> {

    /** Used by login: find the account for the email the user typed, in any letter case. */
    Optional<User> findByEmailIgnoreCase(String email);

    /** Used by registration: reject an email that is already taken. */
    boolean existsByEmailIgnoreCase(String email);

    /** Used by login with a username instead of an email. */
    Optional<User> findByUsernameIgnoreCase(String username);

    boolean existsByUsernameIgnoreCase(String username);

    /** Ids of all frozen accounts (loaded once at startup by FrozenAccounts). */
    @org.springframework.data.jpa.repository.Query("select u.id from User u where u.frozenAt is not null")
    java.util.List<UUID> findFrozenIds();
}
