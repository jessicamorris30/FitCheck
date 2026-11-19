package com.fitcheck.storage;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

/** Pure unit tests for recognizing image formats by their first bytes. */
class ImageTypeTest {

    private static byte[] bytes(int... values) {
        byte[] b = new byte[values.length];
        for (int i = 0; i < values.length; i++) {
            b[i] = (byte) values[i];
        }
        return b;
    }

    @Test
    void recognizesJpegPngAndWebp() {
        assertThat(ImageType.detect(bytes(0xFF, 0xD8, 0xFF, 0xE1, 0, 0))).contains(ImageType.JPEG);
        assertThat(ImageType.detect(bytes(0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0))).contains(ImageType.PNG);
        assertThat(ImageType.detect("RIFF\0\0\0\0WEBPVP8 ".getBytes(StandardCharsets.ISO_8859_1))).contains(ImageType.WEBP);
    }

    @Test
    void rejectsEverythingElse() {
        assertThat(ImageType.detect("GIF89a......".getBytes(StandardCharsets.ISO_8859_1))).isEmpty(); // GIF: not accepted
        assertThat(ImageType.detect("%PDF-1.7....".getBytes(StandardCharsets.ISO_8859_1))).isEmpty();
        assertThat(ImageType.detect("RIFF\0\0\0\0WAVE".getBytes(StandardCharsets.ISO_8859_1))).isEmpty(); // audio, not WebP
        assertThat(ImageType.detect(bytes(0xFF, 0xD8))).isEmpty(); // too short
        assertThat(ImageType.detect(new byte[0])).isEmpty();
    }
}
