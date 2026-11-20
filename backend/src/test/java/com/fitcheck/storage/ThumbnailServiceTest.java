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
    void rejectsBytesThatArentAnImage() {
        assertThatThrownBy(() -> thumbnails.toJpeg("not an image at all".getBytes(), 320))
                .isInstanceOf(IllegalArgumentException.class);
        // Looks like a JPEG for the first few bytes, but isn't one.
        byte[] fakeJpeg = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 1, 2, 3, 4};
        assertThatThrownBy(() -> thumbnails.toJpeg(fakeJpeg, 320)).isInstanceOf(IllegalArgumentException.class);
    }
}
