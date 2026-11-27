package com.fitcheck.storage;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.zip.CRC32;
import javax.imageio.ImageIO;

/** Real (tiny) image files generated in memory, for tests that need decodable photos. */
public final class TestImages {

    private TestImages() {
    }

    public static byte[] jpeg(int width, int height) {
        return encode(width, height, "jpeg", BufferedImage.TYPE_INT_RGB);
    }

    public static byte[] png(int width, int height) {
        return encode(width, height, "png", BufferedImage.TYPE_INT_ARGB);
    }

    private static byte[] encode(int width, int height, String format, int type) {
        BufferedImage image = new BufferedImage(width, height, type);
        Graphics2D g = image.createGraphics();
        g.setColor(new Color(40, 80, 160));
        g.fillRect(0, 0, width, height);
        g.setColor(Color.ORANGE);
        g.fillRect(0, 0, width / 2, height / 4); // something off-center, to tell orientation apart
        g.dispose();
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ImageIO.write(image, format, out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * Adds an EXIF block with the given orientation (1-8) right after the JPEG's start marker,
     * the way a phone camera does.
     */
    public static byte[] withExifOrientation(byte[] jpeg, int orientation) {
        ByteBuffer tiff = ByteBuffer.allocate(26).order(ByteOrder.LITTLE_ENDIAN);
        tiff.put((byte) 'I').put((byte) 'I').putShort((short) 42).putInt(8); // TIFF header, first IFD at 8
        tiff.putShort((short) 1);                                             // 1 entry
        tiff.putShort((short) 0x0112).putShort((short) 3).putInt(1);          // Orientation, SHORT, count 1
        tiff.putShort((short) orientation).putShort((short) 0);               // value (padded to 4 bytes)
        tiff.putInt(0);                                                       // no next IFD
        byte[] exif = ("Exif\0\0").getBytes(StandardCharsets.ISO_8859_1);
        int length = 2 + exif.length + tiff.capacity();

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(jpeg, 0, 2);                                     // SOI (FF D8)
        out.write(0xFF);
        out.write(0xE1);                                           // APP1 = EXIF
        out.write(length >> 8);
        out.write(length & 0xFF);
        out.writeBytes(exif);
        out.writeBytes(tiff.array());
        out.write(jpeg, 2, jpeg.length - 2);
        return out.toByteArray();
    }

    /** A PNG that is just a header claiming the given size, with no real pixel data. */
    public static byte[] pngHeaderClaiming(int width, int height) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.writeBytes(new byte[] {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n'});
        ByteBuffer ihdr = ByteBuffer.allocate(13).putInt(width).putInt(height)
                .put((byte) 8).put((byte) 2).put((byte) 0).put((byte) 0).put((byte) 0); // 8-bit RGB
        writeChunk(out, "IHDR", ihdr.array());
        writeChunk(out, "IEND", new byte[0]);
        return out.toByteArray();
    }

    private static void writeChunk(ByteArrayOutputStream out, String type, byte[] data) {
        byte[] typeBytes = type.getBytes(StandardCharsets.US_ASCII);
        CRC32 crc = new CRC32();
        crc.update(typeBytes);
        crc.update(data);
        out.writeBytes(ByteBuffer.allocate(4).putInt(data.length).array());
        out.writeBytes(typeBytes);
        out.writeBytes(data);
        out.writeBytes(ByteBuffer.allocate(4).putInt((int) crc.getValue()).array());
    }

    public static BufferedImage decode(byte[] bytes) {
        try {
            return ImageIO.read(new java.io.ByteArrayInputStream(bytes));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
