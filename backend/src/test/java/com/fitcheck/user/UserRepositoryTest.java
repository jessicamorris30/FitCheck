package com.fitcheck.user;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;

/**
 * Runs against the local Postgres from docker compose (not an in-memory database), so it
 * tests the real Flyway schema. Each test runs in a transaction that is rolled back at the
 * end, so nothing is left behind in your dev database.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class UserRepositoryTest {

    @Autowired
    private UserRepository users;

    @Test
    void savesUserAndGeneratesIdAndTimestamps() {
        User saved = users.saveAndFlush(new User("ada@example.com", "not-a-real-hash"));

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getUpdatedAt()).isNotNull();
    }

    @Test
    void findsUserByEmailIgnoringCase() {
        users.saveAndFlush(new User("Grace@Example.com", "not-a-real-hash"));

        assertThat(users.findByEmailIgnoreCase("grace@example.com")).isPresent();
        assertThat(users.existsByEmailIgnoreCase("GRACE@EXAMPLE.COM")).isTrue();
        assertThat(users.existsByEmailIgnoreCase("nobody@example.com")).isFalse();
    }
}
