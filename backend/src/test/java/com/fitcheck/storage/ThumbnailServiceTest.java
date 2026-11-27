package com.fitcheck.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.awt.image.BufferedImage;
import java.util.Base64;
import org.junit.jupiter.api.Test;

/** Pure unit tests: no Spring, no storage. */
class ThumbnailServiceTest {

    private final ThumbnailService thumbnails = new ThumbnailService();

    @Test
    void shrinksLargePhotoKeepingProportionsAsJpeg() {
        byte[] thumb = thumbnails.toJpeg(TestImages.png(1600, 1200), 320);

        assertThat(ImageType.detect(thumb)).contains(ImageType.JPEG);
        BufferedImage img = TestImages.decode(thumb);
        assertThat(img.getWidth()).isEqualTo(320);
        assertThat(img.getHeight()).isEqualTo(240);
    }

    @Test
    void tallPhotoIsLimitedByItsHeight() {
        BufferedImage img = TestImages.decode(thumbnails.toJpeg(TestImages.jpeg(600, 1200), 320));
        assertThat(img.getHeight()).isEqualTo(320);
        assertThat(img.getWidth()).isEqualTo(160);
    }

    @Test
    void smallPhotoIsNotEnlarged() {
        BufferedImage img = TestImages.decode(thumbnails.toJpeg(TestImages.png(100, 80), 320));
        assertThat(img.getWidth()).isEqualTo(100);
        assertThat(img.getHeight()).isEqualTo(80);
    }

    @Test
    void readsWebp() {
        // A real 1×1 lossless WebP file. Java can't read WebP by itself; the TwelveMonkeys plugin can.
        byte[] webp = Base64.getDecoder().decode("UklGRhoAAABXRUJQVlA4TA0AAAAvAAAAEAcQERGIiP4HAA==");
        BufferedImage img = TestImages.decode(thumbnails.toJpeg(webp, 320));
        assertThat(img.getWidth()).isEqualTo(1);
    }

    @Test
    void exifOrientationSixTurnsThePictureUpright() {
        // Orientation 6 = "rotate 90° clockwise to display": a wide image becomes tall.
        BufferedImage wide = TestImages.decode(TestImages.jpeg(400, 200));
        BufferedImage upright = ThumbnailService.applyOrientation(wide, 6);
        assertThat(upright.getWidth()).isEqualTo(200);
        assertThat(upright.getHeight()).isEqualTo(400);
    }

    @Test
    void phonePhotoStoredSidewaysBecomesAnUprightThumbnail() {
        // Like a portrait phone photo: pixels saved landscape (4032×3024) plus EXIF "rotate 90° clockwise".
        byte[] photo = TestImages.withExifOrientation(TestImages.jpeg(4032, 3024), 6);

        BufferedImage thumb = TestImages.decode(thumbnails.toJpeg(photo, 320));

        assertThat(thumb.getWidth()).isEqualTo(240);
        assertThat(thumb.getHeight()).isEqualTo(320);
        // The orange patch sits top-left in the saved pixels; turned clockwise it must be top-RIGHT.
        assertThat(isOrange(thumb.getRGB(230, 10))).isTrue();
        assertThat(isOrange(thumb.getRGB(10, 10))).isFalse();
        assertThat(isOrange(thumb.getRGB(230, 310))).isFalse();
    }

    @Test
    void manyPhotosAtOnceAllSucceed() throws Exception {
        // Only MAX_CONCURRENT are processed at the same moment; the rest must wait, not fail.
        byte[] photo = TestImages.jpeg(2000, 1500);
        try (var pool = java.util.concurrent.Executors.newFixedThreadPool(8)) {
            var results = pool.invokeAll(java.util.Collections.nCopies(8,
                    (java.util.concurrent.Callable<byte[]>) () -> thumbnails.toJpeg(photo, 320)));
            for (var result : results) {
                assertThat(TestImages.decode(result.get()).getWidth()).isEqualTo(320);
            }
        }
    }

    @Test
    void refusesAnImageClaimingToBeEnormousBeforeUnpackingIt() {
        // A tiny file whose header says 30,000×30,000 (900 megapixels).
        byte[] bomb = TestImages.pngHeaderClaiming(30_000, 30_000);

        assertThatThrownBy(() -> thumbnails.toJpeg(bomb, 320))
                .isInstanceOf(ImageTooLargeException.class)
                .hasMessageContaining("30000×30000");
    }

    private static boolean isOrange(int rgb) {
        int r = (rgb >> 16) & 0xFF;
        int b = rgb & 0xFF;
        return r > 200 && b < 80;
    }

    @Test
    void rejectsBytesThatArentAnImage() {
        assertThatThrownBy(() -> thumbnails.toJpeg("not an image at all".getBytes(), 320))
                .isInstanceOf(IllegalArgumentException.class);
        // Looks like a JPEG for the first few bytes, but isn't one.
        byte[] fakeJpeg = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 1, 2, 3, 4};
        assertThatThrownBy(() -> thumbnails.toJpeg(fakeJpeg, 320)).isInstanceOf(IllegalArgumentException.class);
    }
}
