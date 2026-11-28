package com.fitcheck.admin;

import java.nio.ByteBuffer;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Locale;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * Time-based one-time codes (TOTP, RFC 6238): the 6-digit codes authenticator apps (Google
 * Authenticator, Microsoft Authenticator, 1Password...) show, changing every 30 seconds.
 *
 * <p>How it works: the server and the app share a random secret (scanned once from a QR code).
 * Both compute HMAC-SHA1(secret, current 30-second step number) and turn it into 6 digits. Same
 * secret + same time → same code, with nothing sent over the network.
 *
 * <p>Small enough to write here instead of adding a library; checked against the RFC's own test
 * values in TotpTest.
 */
public final class Totp {

    public static final int DIGITS = 6;
    public static final long STEP_SECONDS = 30;
    private static final int SECRET_BYTES = 20; // 160 bits, as RFC 4226 recommends
    private static final SecureRandom RANDOM = new SecureRandom();

    private Totp() {
    }

    /** A new random secret, as Base32 text (the form authenticator apps expect). */
    public static String newSecret() {
        byte[] bytes = new byte[SECRET_BYTES];
        RANDOM.nextBytes(bytes);
        return Base32.encode(bytes);
    }

    /** The 30-second step number for a moment in time. */
    public static long stepAt(long epochSeconds) {
        return Math.floorDiv(epochSeconds, STEP_SECONDS);
    }

    /** The code for one step, e.g. "042318" (always 6 digits, leading zeros kept). */
    public static String code(String base32Secret, long step) {
        try {
            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec(Base32.decode(base32Secret), "HmacSHA1"));
            byte[] hash = mac.doFinal(ByteBuffer.allocate(8).putLong(step).array());
            // "Dynamic truncation" (RFC 4226): take 4 bytes at an offset given by the last nibble.
            int offset = hash[hash.length - 1] & 0x0f;
            int binary = ((hash[offset] & 0x7f) << 24) | ((hash[offset + 1] & 0xff) << 16)
                    | ((hash[offset + 2] & 0xff) << 8) | (hash[offset + 3] & 0xff);
            return String.format(Locale.ROOT, "%06d", binary % 1_000_000);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HmacSHA1 unavailable", e);
        }
    }

    /**
     * Checks a typed code against the current step and one step either side (so a phone clock a
     * little off, or typing slowly, still works).
     *
     * @return the step that matched, or -1 if none did. The caller stores it so the same code
     *         can't be used twice.
     */
    public static long matchingStep(String base32Secret, String typed, long currentStep) {
        String clean = typed == null ? "" : typed.replaceAll("\\s", "");
        if (!clean.matches("\\d{6}")) {
            return -1;
        }
        for (long step = currentStep - 1; step <= currentStep + 1; step++) {
            // Constant-time comparison, so timing doesn't reveal how many digits were right.
            if (MessageDigest.isEqual(code(base32Secret, step).getBytes(), clean.getBytes())) {
                return step;
            }
        }
        return -1;
    }

    /**
     * The link an authenticator app reads from the QR code, e.g.
     * otpauth://totp/FitCheck:me@example.com?secret=...&issuer=FitCheck
     */
    public static String otpauthUri(String base32Secret, String accountName) {
        String label = urlEncode("FitCheck:" + accountName);
        return "otpauth://totp/" + label + "?secret=" + base32Secret + "&issuer=FitCheck&digits=" + DIGITS
                + "&period=" + STEP_SECONDS;
    }

    private static String urlEncode(String s) {
        return java.net.URLEncoder.encode(s, java.nio.charset.StandardCharsets.UTF_8).replace("+", "%20");
    }

    /** RFC 4648 Base32 (A-Z, 2-7), without padding: how TOTP secrets are written. */
    static final class Base32 {

        private static final String ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";

        private Base32() {
        }

        static String encode(byte[] data) {
            StringBuilder out = new StringBuilder();
            int buffer = 0;
            int bits = 0;
            for (byte b : data) {
                buffer = (buffer << 8) | (b & 0xff);
                bits += 8;
                while (bits >= 5) {
                    out.append(ALPHABET.charAt((buffer >> (bits - 5)) & 31));
                    bits -= 5;
                }
            }
            if (bits > 0) {
                out.append(ALPHABET.charAt((buffer << (5 - bits)) & 31));
            }
            return out.toString();
        }

        static byte[] decode(String text) {
            String clean = text.replace("=", "").replace(" ", "").toUpperCase(Locale.ROOT);
            ByteBuffer out = ByteBuffer.allocate(clean.length() * 5 / 8);
            int buffer = 0;
            int bits = 0;
            for (char c : clean.toCharArray()) {
                int value = ALPHABET.indexOf(c);
                if (value < 0) {
                    throw new IllegalArgumentException("Not Base32: " + c);
                }
                buffer = (buffer << 5) | value;
                bits += 5;
                if (bits >= 8) {
                    out.put((byte) (buffer >> (bits - 8)));
                    bits -= 8;
                }
            }
            return out.array();
        }
    }
}
