package com.fitcheck.storage;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Optional;

/**
 * The image formats FitCheck accepts, recognized by their first bytes ("magic numbers").
 *
 * <p>Why not trust the file name or the Content-Type the browser sends? Both are just labels
 * chosen by the client: anyone can upload {@code virus.exe} renamed to {@code photo.jpg} with
 * {@code Content-Type: image/jpeg}. The first bytes of a real image file are fixed by its format,
 * so checking them is a much more honest test of what was actually uploaded.
 */
public enum ImageType {

    JPEG("image/jpeg", "jpg"),
    PNG("image/png", "png"),
    WEBP("image/webp", "webp");

    private final String contentType;
    private final String extension;

    ImageType(String contentType, String extension) {
        this.contentType = contentType;
        this.extension = extension;
    }

    public String contentType() {
        return contentType;
    }

    public String extension() {
        return extension;
    }

    /** Reads the first 12 bytes of the stream and identifies the format, if it's one we accept. */
    public static Optional<ImageType> detect(InputStream in) throws IOException {
        byte[] head = in.readNBytes(12);
        return detect(head);
    }

    /** Same check on bytes already in memory (only the first 12 are looked at). */
    public static Optional<ImageType> detect(byte[] head) {
        // JPEG: FF D8 FF
        if (startsWith(head, 0, 0xFF, 0xD8, 0xFF)) {
            return Optional.of(JPEG);
        }
        // PNG: 89 'P' 'N' 'G' 0D 0A 1A 0A
        if (startsWith(head, 0, 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)) {
            return Optional.of(PNG);
        }
        // WebP: "RIFF" <4 size bytes> "WEBP"
        if (startsWith(head, 0, 'R', 'I', 'F', 'F') && startsWith(head, 8, 'W', 'E', 'B', 'P')) {
            return Optional.of(WEBP);
        }
        return Optional.empty();
    }

    private static boolean startsWith(byte[] data, int offset, int... expected) {
        if (data.length < offset + expected.length) {
            return false;
        }
        int[] actual = new int[expected.length];
        for (int i = 0; i < expected.length; i++) {
            actual[i] = data[offset + i] & 0xFF; // bytes are signed in Java; & 0xFF gives 0-255
        }
        return Arrays.equals(actual, expected);
    }
}
