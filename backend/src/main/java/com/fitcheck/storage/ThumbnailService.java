package com.fitcheck.storage;

import com.drew.imaging.ImageMetadataReader;
import com.drew.metadata.Metadata;
import com.drew.metadata.exif.ExifIFD0Directory;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Iterator;
import java.util.concurrent.Semaphore;
import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.ImageOutputStream;
import org.springframework.stereotype.Service;

/**
 * Makes small JPEG copies of photos.
 *
 * <p>Used for wardrobe thumbnails (so the grid downloads ~20 KB instead of several MB per item)
 * and, from step 4.3, for showing the wardrobe to Claude cheaply.
 *
 * <p>Steps: check the size in the file header → decode the photo (JPEG/PNG built into Java, WebP via
 * the TwelveMonkeys plugin, which ImageIO discovers automatically) → shrink it, flattening
 * transparency onto white → turn it upright using its EXIF orientation → encode as JPEG.
 * A 12 MP phone photo needs ~48 MB at its peak (it was ~95 MB when the full-size photo was rotated
 * too), and at most {@link #MAX_CONCURRENT} photos are processed at once.
 */
@Service
public class ThumbnailService {

    /** Longest side of a wardrobe thumbnail, in pixels. */
    public static final int THUMBNAIL_SIZE = 320;
    private static final float JPEG_QUALITY = 0.8f;
    /** Largest image accepted: 100 megapixels, far above any phone photo (12-50 MP). */
    public static final long MAX_PIXELS = 100_000_000L;
    /**
     * At most 2 photos unpacked at the same moment (~48 MB each for a 12 MP phone photo), so many
     * uploads at once can't add up to more than the container's 1 GiB. Others wait their turn,
     * about a second each; with half a CPU, more in parallel wouldn't be faster anyway.
     */
    static final int MAX_CONCURRENT = 2;
    private static final Semaphore PERMITS = new Semaphore(MAX_CONCURRENT, true);

    /**
     * @param original the photo's bytes (JPEG, PNG or WebP)
     * @param maxSide  the longest side of the result; smaller photos are not enlarged
     * @return JPEG bytes
     * @throws IllegalArgumentException if the bytes can't be decoded as an image
     */
    public byte[] toJpeg(byte[] original, int maxSide) {
        int orientation = readOrientation(original);
        BufferedImage image;
        acquirePermit();
        try {
            // The memory-hungry part: the full-size photo exists only inside this block.
            image = shrink(decode(original), maxSide);
        } finally {
            PERMITS.release();
        }
        // Turned upright AFTER shrinking: rotating a 320px image costs nothing, a 12-megapixel one ~48 MB.
        image = applyOrientation(image, orientation);
        return encodeJpeg(image);
    }

    private static void acquirePermit() {
        try {
            PERMITS.acquire();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting to process an image", e);
        }
    }

    /**
     * Unpacks the photo at full size, after checking its size in the file header.
     *
     * <p>Why not unpack fewer pixels? Java's image reader can skip pixels ("subsampling") but can't
     * average them, and skipping makes stripes and knits change colour or vanish in the thumbnail
     * (tried and measured: navy/cream stripes came out solid navy). For a wardrobe
     * that's worse than spending the memory, so the memory is capped by {@link #PERMITS} instead.
     *
     * @throws ImageTooLargeException if the image claims more than {@link #MAX_PIXELS}
     */
    private static BufferedImage decode(byte[] bytes) {
        try (ImageInputStream in = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
            Iterator<ImageReader> readers = in == null ? null : ImageIO.getImageReaders(in);
            if (readers == null || !readers.hasNext()) {
                throw new IllegalArgumentException("Not a readable image");
            }
            ImageReader reader = readers.next();
            try {
                reader.setInput(in, true, true);
                int width = reader.getWidth(0);
                int height = reader.getHeight(0);
                // A small file can claim to be enormous (a "decompression bomb"): unpacking a PNG
                // of 30,000×30,000 needs ~2.7 GB and would crash the app. Checked before unpacking.
                if ((long) width * height > MAX_PIXELS) {
                    throw new ImageTooLargeException(width, height);
                }
                BufferedImage image = reader.read(0);
                if (image == null) {
                    throw new IllegalArgumentException("Not a readable image");
                }
                return image;
            } finally {
                reader.dispose();
            }
        } catch (IOException e) {
            throw new IllegalArgumentException("Not a readable image", e);
        }
    }

    /**
     * EXIF orientation 1-8 (1 = already upright). Phones usually save the pixels as the sensor saw
     * them and just add this note; browsers obey it, Java's ImageIO doesn't, so we apply it ourselves.
     */
    private static int readOrientation(byte[] bytes) {
        try {
            Metadata metadata = ImageMetadataReader.readMetadata(new ByteArrayInputStream(bytes));
            ExifIFD0Directory exif = metadata.getFirstDirectoryOfType(ExifIFD0Directory.class);
            if (exif != null && exif.containsTag(ExifIFD0Directory.TAG_ORIENTATION)) {
                return exif.getInt(ExifIFD0Directory.TAG_ORIENTATION);
            }
        } catch (Exception e) {
            // No or unreadable metadata (common for PNG/WebP): treat as upright.
        }
        return 1;
    }

    /** Rotates/flips the pixels so the image looks the way the EXIF orientation says it should. */
    static BufferedImage applyOrientation(BufferedImage src, int orientation) {
        if (orientation <= 1 || orientation > 8) {
            return src;
        }
        int w = src.getWidth();
        int h = src.getHeight();
        boolean swap = orientation >= 5; // 5-8 involve a 90° turn, so width and height swap
        AffineTransform t = new AffineTransform();
        switch (orientation) {
            case 2 -> { t.translate(w, 0); t.scale(-1, 1); }                 // mirrored
            case 3 -> { t.translate(w, h); t.rotate(Math.PI); }               // upside down
            case 4 -> { t.translate(0, h); t.scale(1, -1); }                 // flipped vertically
            case 5 -> { t.rotate(Math.PI / 2); t.scale(1, -1); }              // transposed
            case 6 -> { t.translate(h, 0); t.rotate(Math.PI / 2); }           // rotate 90° clockwise
            case 7 -> { t.scale(-1, 1); t.translate(-h, 0); t.translate(0, w); t.rotate(3 * Math.PI / 2); } // transverse
            case 8 -> { t.translate(0, w); t.rotate(3 * Math.PI / 2); }       // rotate 90° counter-clockwise
            default -> { }
        }
        // RGB: by now shrink() has already flattened any transparency onto white.
        BufferedImage out = new BufferedImage(swap ? h : w, swap ? w : h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = out.createGraphics();
        g.drawImage(src, t, null);
        g.dispose();
        return out;
    }

    /**
     * Shrinks in steps of at most half the size at a time. One big jump down (e.g. 4000px → 320px)
     * skips most pixels and looks jagged; halving repeatedly keeps it smooth.
     */
    private static BufferedImage shrink(BufferedImage src, int maxSide) {
        int w = src.getWidth();
        int h = src.getHeight();
        double scale = Math.min(1.0, (double) maxSide / Math.max(w, h));
        int targetW = Math.max(1, (int) Math.round(w * scale));
        int targetH = Math.max(1, (int) Math.round(h * scale));

        BufferedImage current = src;
        do {
            w = Math.max(targetW, w / 2);
            h = Math.max(targetH, h / 2);
            current = draw(current, w, h);
        } while (w != targetW || h != targetH);
        return current;
    }

    /** Draws the image at a new size onto a white RGB canvas (JPEG has no transparency). */
    private static BufferedImage draw(BufferedImage src, int w, int h) {
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = out.createGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, w, h);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.drawImage(src, 0, 0, w, h, null);
        g.dispose();
        return out;
    }

    private static byte[] encodeJpeg(BufferedImage image) {
        ImageWriter writer = ImageIO.getImageWritersByFormatName("jpeg").next();
        ImageWriteParam params = writer.getDefaultWriteParam();
        params.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
        params.setCompressionQuality(JPEG_QUALITY);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ImageOutputStream ios = ImageIO.createImageOutputStream(out)) {
            writer.setOutput(ios);
            writer.write(null, new IIOImage(image, null, null), params);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not encode JPEG", e);
        } finally {
            writer.dispose();
        }
        return out.toByteArray();
    }
}
