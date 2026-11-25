package com.sweethomecompany.crm.property;

import net.coobird.thumbnailator.Thumbnails;
import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
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

    private final boolean webpAvailable;

    public ImagePipelineService() {
        this.webpAvailable = detectWebpSupport();
    }

    public boolean isWebpAvailable() {
        return webpAvailable;
    }

    public List<Variant> process(byte[] originalImageBytes) throws IOException {
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

            variants.add(new Variant(size, "jpeg", encode(resized, "jpeg")));

            if (webpAvailable) {
                variants.add(new Variant(size, "webp", encode(resized, "webp")));
            }
        }
        return variants;
    }

    private static byte[] encode(BufferedImage image, String formatName) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, formatName, out);
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
