package com.fitcheck.admin;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

/** The one-time code maths, checked against the official test values (no Spring). */
class TotpTest {

    /** RFC 6238 Appendix B uses this ASCII secret for its SHA-1 test values. */
    private static final String RFC_SECRET =
            Totp.Base32.encode("12345678901234567890".getBytes(StandardCharsets.US_ASCII));

    @Test
    void matchesTheRfcTestValues() {
        // RFC 6238 Appendix B (SHA-1), last 6 of its 8 digits: time → code.
        assertThat(Totp.code(RFC_SECRET, Totp.stepAt(59))).isEqualTo("287082");
        assertThat(Totp.code(RFC_SECRET, Totp.stepAt(1111111109))).isEqualTo("081804");
        assertThat(Totp.code(RFC_SECRET, Totp.stepAt(1111111111))).isEqualTo("050471");
        assertThat(Totp.code(RFC_SECRET, Totp.stepAt(1234567890))).isEqualTo("005924");
        assertThat(Totp.code(RFC_SECRET, Totp.stepAt(2000000000))).isEqualTo("279037");
    }

    @Test
    void acceptsTheCurrentCodeAndOneStepEitherSideOnly() {
        String secret = Totp.newSecret();
        long now = 1_000_000;
        assertThat(Totp.matchingStep(secret, Totp.code(secret, now), now)).isEqualTo(now);
        assertThat(Totp.matchingStep(secret, Totp.code(secret, now - 1), now)).isEqualTo(now - 1);
        assertThat(Totp.matchingStep(secret, Totp.code(secret, now + 1), now)).isEqualTo(now + 1);
        assertThat(Totp.matchingStep(secret, Totp.code(secret, now - 2), now)).isEqualTo(-1);
    }

    @Test
    void ignoresSpacesAndRefusesNonsense() {
        String secret = Totp.newSecret();
        String code = Totp.code(secret, 42);
        assertThat(Totp.matchingStep(secret, code.substring(0, 3) + " " + code.substring(3), 42)).isEqualTo(42);
        assertThat(Totp.matchingStep(secret, "12345", 42)).isEqualTo(-1);
        assertThat(Totp.matchingStep(secret, "abcdef", 42)).isEqualTo(-1);
        assertThat(Totp.matchingStep(secret, null, 42)).isEqualTo(-1);
    }

    @Test
    void base32RoundTripsAndSecretsAreLongAndRandom() {
        byte[] data = {0, 1, 2, (byte) 250, (byte) 255, 77};
        assertThat(Totp.Base32.decode(Totp.Base32.encode(data))).startsWith(data);
        String a = Totp.newSecret();
        assertThat(a).hasSize(32).matches("[A-Z2-7]+");
        assertThat(a).isNotEqualTo(Totp.newSecret());
    }

    @Test
    void buildsTheLinkAuthenticatorAppsRead() {
        assertThat(Totp.otpauthUri("JBSWY3DPEHPK3PXP", "me@example.com"))
                .isEqualTo("otpauth://totp/FitCheck%3Ame%40example.com?secret=JBSWY3DPEHPK3PXP&issuer=FitCheck&digits=6&period=30");
    }
}
