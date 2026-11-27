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
        RefreshToken stored = tokens.findByTokenHash(SecureTokens.sha256(issued.value())).orElseThrow();
        assertThat(stored.isRevoked()).isFalse();
    }

    @Test
    void rotationRetiresOldTokenAndKeepsTheFamily() {
        var first = service.issue(newUser());
        var second = service.rotate(first.value());

        RefreshToken old = tokens.findByTokenHash(SecureTokens.sha256(first.value())).orElseThrow();
        RefreshToken current = tokens.findByTokenHash(SecureTokens.sha256(second.value())).orElseThrow();

        assertThat(old.isRevoked()).isTrue();
        assertThat(old.getReplacedBy()).isEqualTo(current.getId());
        assertThat(current.getFamilyId()).isEqualTo(old.getFamilyId());
        assertThat(current.isRevoked()).isFalse();
    }

    /** The same service, with a clock this far in the future. */
    private RefreshTokenService later(Duration by) {
        return new RefreshTokenService(tokens, properties, Clock.offset(Clock.systemUTC(), by));
    }

    private boolean isRevoked(String rawToken) {
        return tokens.findByTokenHash(SecureTokens.sha256(rawToken)).orElseThrow().isRevoked();
    }

    @Test
    void reuseRevokesEveryTokenInTheFamily() {
        var first = service.issue(newUser());
        var second = service.rotate(first.value());

        // The stolen first token is replayed a minute later, long after the two-tab grace period.
        assertThatThrownBy(() -> later(Duration.ofMinutes(1)).rotate(first.value()))
                .isInstanceOf(UnauthorizedException.class);

        assertThat(isRevoked(second.value())).isTrue();
    }

    @Test
    void twoTabsRefreshingTogetherBothStayLoggedIn() {
        var first = service.issue(newUser());
        var tabA = service.rotate(first.value());
        // Tab B sent the same (now replaced) token a moment later: it gets its own new token...
        var tabB = service.rotate(first.value());

        assertThat(tabB.value()).isNotEqualTo(tabA.value());
        assertThat(isRevoked(tabA.value())).isFalse();
        assertThat(isRevoked(tabB.value())).isFalse();
        // ...and both keep working: nothing was treated as theft.
        service.rotate(tabB.value());
        service.rotate(tabA.value());
    }

    @Test
    void graceEndsAfterAFewSeconds() {
        var first = service.issue(newUser());
        service.rotate(first.value());

        assertThatThrownBy(() -> later(RefreshTokenService.TWO_TAB_GRACE.plusSeconds(1)).rotate(first.value()))
                .isInstanceOf(UnauthorizedException.class);
    }

    @Test
    void noGraceAfterLogout() {
        var first = service.issue(newUser());
        var second = service.rotate(first.value());
        service.revoke(second.value()); // logged out right after refreshing

        // The old token must not bring the session back, even within the grace period.
        assertThatThrownBy(() -> service.rotate(first.value())).isInstanceOf(UnauthorizedException.class);
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
