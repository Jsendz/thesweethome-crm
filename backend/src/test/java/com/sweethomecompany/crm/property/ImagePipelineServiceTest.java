package com.sweethomecompany.crm.property;

import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.zip.CRC32;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ImagePipelineServiceTest {

    private static final List<String> EXPECTED_SIZES =
            List.of("thumbnail", "card", "gallery", "full_detail");
    private static final int[] EXPECTED_MAX_DIMENSION = {400, 800, 1600, 3000};

    @Test
    void producesAllFourSizesInJpegAtMinimum() throws IOException {
        byte[] original = Files.readAllBytes(Path.of("src/test/resources/test-photo.jpg"));
        List<ImagePipelineService.Variant> variants = new ImagePipelineService().process(original);

        for (int i = 0; i < EXPECTED_SIZES.size(); i++) {
            String size = EXPECTED_SIZES.get(i);
            ImagePipelineService.Variant jpeg = variantFor(variants, size, "jpeg")
                    .orElseThrow(() -> new AssertionError("missing jpeg variant for size " + size));
            BufferedImage decoded = ImageIO.read(new ByteArrayInputStream(jpeg.bytes()));
            assertThat(decoded).as("decoded %s jpeg", size).isNotNull();
            assertThat(Math.max(decoded.getWidth(), decoded.getHeight()))
                    .as("%s longest edge", size)
                    .isLessThanOrEqualTo(EXPECTED_MAX_DIMENSION[i]);
        }
    }

    @Test
    void includesWebpVariantsOnlyWhenWebpIsAvailableOnThisPlatform() throws IOException {
        byte[] original = Files.readAllBytes(Path.of("src/test/resources/test-photo.jpg"));
        ImagePipelineService service = new ImagePipelineService();
        List<ImagePipelineService.Variant> variants = service.process(original);

        boolean hasWebp = variants.stream().anyMatch(v -> v.format().equals("webp"));
        assertThat(hasWebp).isEqualTo(service.isWebpAvailable());

        if (service.isWebpAvailable()) {
            for (String size : EXPECTED_SIZES) {
                ImagePipelineService.Variant webp = variantFor(variants, size, "webp")
                        .orElseThrow(() -> new AssertionError("missing webp variant for size " + size));
                assertThat(ImageIO.read(new ByteArrayInputStream(webp.bytes())))
                        .as("decoded %s webp", size)
                        .isNotNull();
            }
        }
    }

    @Test
    void rejectsBytesThatAreNotADecodableImage() {
        assertThatThrownBy(() -> new ImagePipelineService().process("not an image".getBytes()))
                .isInstanceOf(IOException.class);
    }

    // Final-review finding: a non-RGB source (an RGBA PNG, as produced by
    // macOS screenshots and many floorplan exports) used to make ImageIO's
    // JPEG encoder silently write zero bytes for every size, producing a
    // "successful" upload whose JPEG variant URLs were all empty/broken.
    @Test
    void producesNonEmptyDecodableJpegVariantsForAnRgbaSourceImage() throws IOException {
        BufferedImage rgba = new BufferedImage(2000, 1500, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = rgba.createGraphics();
        graphics.setColor(new Color(10, 20, 30, 128));
        graphics.fillRect(0, 0, 2000, 1500);
        graphics.dispose();
        ByteArrayOutputStream pngBytes = new ByteArrayOutputStream();
        ImageIO.write(rgba, "png", pngBytes);

        List<ImagePipelineService.Variant> variants = new ImagePipelineService().process(pngBytes.toByteArray());

        for (String size : EXPECTED_SIZES) {
            ImagePipelineService.Variant jpeg = variantFor(variants, size, "jpeg")
                    .orElseThrow(() -> new AssertionError("missing jpeg variant for size " + size));
            assertThat(jpeg.bytes()).as("%s jpeg bytes", size).isNotEmpty();
            BufferedImage decoded = ImageIO.read(new ByteArrayInputStream(jpeg.bytes()));
            assertThat(decoded).as("decoded %s jpeg", size).isNotNull();
        }
    }

    // Final-review finding: nothing capped the pixel count a decode would
    // allocate, so a tiny file declaring an enormous width/height (a
    // decompression-bomb-style upload) could exhaust heap before any size
    // validation ran. This PNG has a valid header declaring 30000x30000 but
    // no actual pixel data (no IDAT chunk) - constructed by hand so the test
    // itself never allocates the ~2.7GB a real image that size would need;
    // the dimension check must reject it from the header alone, before
    // attempting a full decode.
    @Test
    void rejectsImagesWhoseDeclaredDimensionsExceedTheSaneMaximumBeforeDecodingPixels() throws IOException {
        byte[] headerOnlyPng = pngWithDeclaredDimensionsButNoPixelData(30000, 30000);

        assertThatThrownBy(() -> new ImagePipelineService().process(headerOnlyPng))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("exceed");
    }

    private static byte[] pngWithDeclaredDimensionsButNoPixelData(int width, int height) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(new byte[] {(byte) 137, 80, 78, 71, 13, 10, 26, 10});

        ByteArrayOutputStream ihdrData = new ByteArrayOutputStream();
        DataOutputStream ihdr = new DataOutputStream(ihdrData);
        ihdr.writeInt(width);
        ihdr.writeInt(height);
        ihdr.writeByte(8); // bit depth
        ihdr.writeByte(2); // color type: truecolor
        ihdr.writeByte(0); // compression method
        ihdr.writeByte(0); // filter method
        ihdr.writeByte(0); // interlace method
        writePngChunk(out, "IHDR", ihdrData.toByteArray());
        writePngChunk(out, "IEND", new byte[0]);
        return out.toByteArray();
    }

    private static void writePngChunk(ByteArrayOutputStream out, String type, byte[] data) throws IOException {
        DataOutputStream dos = new DataOutputStream(out);
        dos.writeInt(data.length);
        byte[] typeBytes = type.getBytes(StandardCharsets.US_ASCII);
        out.write(typeBytes);
        out.write(data);
        CRC32 crc = new CRC32();
        crc.update(typeBytes);
        crc.update(data);
        dos.writeInt((int) crc.getValue());
    }

    private static Optional<ImagePipelineService.Variant> variantFor(
            List<ImagePipelineService.Variant> variants, String size, String format) {
        return variants.stream()
                .filter(v -> v.size().equals(size) && v.format().equals(format))
                .findFirst();
    }
}
