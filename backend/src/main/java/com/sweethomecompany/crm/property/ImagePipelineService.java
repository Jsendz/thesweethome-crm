package com.sweethomecompany.crm.property;

import net.coobird.thumbnailator.Thumbnails;
import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Resizes an uploaded photo into the four fixed sizes the CRM displays
 * (thumbnail/card/gallery/full-detail) and encodes each as JPEG, and as
 * WebP when a working WebP encoder is available on this JVM.
 *
 * <p>Re-encoding through {@link BufferedImage} naturally strips the
 * original EXIF/GPS metadata, and Thumbnailator reads EXIF orientation
 * and auto-rotates during resize — so no separate "strip EXIF" or
 * "auto-orient" step is needed beyond what happens here.
 */
@Component
public class ImagePipelineService {

    public record Variant(String size, String format, byte[] bytes) {
    }

    private static final Map<String, Integer> SIZES = new LinkedHashMap<>();

    static {
        SIZES.put("thumbnail", 400);
        SIZES.put("card", 800);
        SIZES.put("gallery", 1600);
        SIZES.put("full_detail", 3000);
    }

    // Comfortably above anything a real estate listing photo or floorplan
    // export would legitimately be (a 45-megapixel medium-format camera is
    // ~8200x5500), while still bounding how large a decode this service will
    // ever attempt - checked from the file header alone (see
    // assertSaneDimensions), before any pixel data is actually decoded, so a
    // small file that lies about having an enormous width/height can't be
    // used to exhaust heap.
    private static final long MAX_DECODED_PIXELS = 40_000_000L;

    private final boolean webpAvailable;

    public ImagePipelineService() {
        this.webpAvailable = detectWebpSupport();
    }

    public boolean isWebpAvailable() {
        return webpAvailable;
    }

    public List<Variant> process(byte[] originalImageBytes) throws IOException {
        assertSaneDimensions(originalImageBytes);

        BufferedImage original = ImageIO.read(new ByteArrayInputStream(originalImageBytes));
        if (original == null) {
            throw new IOException("Could not decode image bytes - unsupported or corrupt image data");
        }

        List<Variant> variants = new ArrayList<>();
        for (Map.Entry<String, Integer> entry : SIZES.entrySet()) {
            String size = entry.getKey();
            int maxDimension = entry.getValue();

            BufferedImage resized = Thumbnails.of(new ByteArrayInputStream(originalImageBytes))
                    .size(maxDimension, maxDimension)
                    .asBufferedImage();

            variants.add(new Variant(size, "jpeg", encodeJpeg(resized)));

            if (webpAvailable) {
                variants.add(new Variant(size, "webp", encode(resized, "webp")));
            }
        }
        return variants;
    }

    private static void assertSaneDimensions(byte[] originalImageBytes) throws IOException {
        try (ImageInputStream input = ImageIO.createImageInputStream(new ByteArrayInputStream(originalImageBytes))) {
            if (input == null) {
                throw new IOException("Could not decode image bytes - unsupported or corrupt image data");
            }
            Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) {
                throw new IOException("Could not decode image bytes - unsupported or corrupt image data");
            }
            ImageReader reader = readers.next();
            try {
                reader.setInput(input);
                int width = reader.getWidth(0);
                int height = reader.getHeight(0);
                if ((long) width * height > MAX_DECODED_PIXELS) {
                    throw new IOException("Image dimensions " + width + "x" + height
                            + " exceed the maximum of " + MAX_DECODED_PIXELS + " pixels");
                }
            } finally {
                reader.dispose();
            }
        }
    }

    // JPEG has no alpha channel. ImageIO's JPEG writer silently writes zero
    // bytes (rather than erroring) for a source type other than plain RGB -
    // observed with TYPE_INT_ARGB, as produced by PNG screenshots and many
    // floorplan exports - so flatten onto an opaque white RGB canvas first.
    private static byte[] encodeJpeg(BufferedImage image) throws IOException {
        BufferedImage rgb = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = rgb.createGraphics();
        graphics.setColor(Color.WHITE);
        graphics.fillRect(0, 0, image.getWidth(), image.getHeight());
        graphics.drawImage(image, 0, 0, null);
        graphics.dispose();
        return encode(rgb, "jpeg");
    }

    private static byte[] encode(BufferedImage image, String formatName) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        boolean wrote = ImageIO.write(image, formatName, out);
        if (!wrote || out.size() == 0) {
            throw new IOException("No " + formatName + " encoder produced output for this image");
        }
        return out.toByteArray();
    }

    private static boolean detectWebpSupport() {
        try {
            BufferedImage probe = new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB);
            byte[] encoded = encode(probe, "webp");
            return encoded.length > 0;
        } catch (Exception e) {
            // No registered WebP ImageWriter, or its native library failed to
            // load on this platform - fall back to JPEG-only everywhere.
            return false;
        }
    }
}
