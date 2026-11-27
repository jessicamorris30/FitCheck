package com.fitcheck.storage;

/** The image claims more pixels than {@link ThumbnailService#MAX_PIXELS}; nothing was unpacked. */
public class ImageTooLargeException extends IllegalArgumentException {

    public ImageTooLargeException(int width, int height) {
        super("Image is " + width + "×" + height + " pixels, more than "
                + ThumbnailService.MAX_PIXELS / 1_000_000 + " megapixels");
    }
}
