package com.fitcheck.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fitcheck.common.exception.UnauthorizedException;
import com.fitcheck.config.JwtProperties;
import com.fitcheck.user.User;
import com.fitcheck.user.UserRepository;
import java.time.Clock;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

/**
 * Checks what RefreshTokenService stores in the database. Rolled back after each test.
 */
@SpringBootTest
@Transactional
class RefreshTokenServiceTest {

    @Autowired
    private RefreshTokenService service;

    @Autowired
    private RefreshTokenRepository tokens;

    @Autowired
    private UserRepository users;

    @Autowired
    private JwtProperties properties;

    private User newUser() {
        return users.saveAndFlush(new User("rt-" + System.nanoTime() + "@example.com", "hash"));
    }

    @Test
    void storesOnlyTheHashNeverTheToken() {
        var issued = service.issue(newUser());

        assertThat(tokens.findByTokenHash(issued.value())).isEmpty();
        RefreshToken stored = tokens.findByTokenHash(RefreshTokenService.hash(issued.value())).orElseThrow();
        assertThat(stored.isRevoked()).isFalse();
    }

    @Test
    void rotationRetiresOldTokenAndKeepsTheFamily() {
        var first = service.issue(newUser());
        var second = service.rotate(first.value());

        RefreshToken old = tokens.findByTokenHash(RefreshTokenService.hash(first.value())).orElseThrow();
        RefreshToken current = tokens.findByTokenHash(RefreshTokenService.hash(second.value())).orElseThrow();

        assertThat(old.isRevoked()).isTrue();
        assertThat(old.getReplacedBy()).isEqualTo(current.getId());
        assertThat(current.getFamilyId()).isEqualTo(old.getFamilyId());
        assertThat(current.isRevoked()).isFalse();
    }

    @Test
    void reuseRevokesEveryTokenInTheFamily() {
        var first = service.issue(newUser());
        var second = service.rotate(first.value());

        assertThatThrownBy(() -> service.rotate(first.value())).isInstanceOf(UnauthorizedException.class);

        RefreshToken current = tokens.findByTokenHash(RefreshTokenService.hash(second.value())).orElseThrow();
        assertThat(current.isRevoked()).isTrue();
    }

    @Test
    void expiredTokenIsRejected() {
        var issued = service.issue(newUser());

        // Same service, but its clock is 15 days in the future (tokens live 14 days).
        var future = new RefreshTokenService(tokens, properties,
                Clock.offset(Clock.systemUTC(), Duration.ofDays(15)));

        assertThatThrownBy(() -> future.rotate(issued.value())).isInstanceOf(UnauthorizedException.class);
    }

    @Test
    void unknownTokenIsRejected() {
        assertThatThrownBy(() -> service.rotate("made-up-token")).isInstanceOf(UnauthorizedException.class);
    }
}
