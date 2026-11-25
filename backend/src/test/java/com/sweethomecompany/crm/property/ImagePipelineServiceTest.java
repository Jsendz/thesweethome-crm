package com.sweethomecompany.crm.property;

import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

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

    private static Optional<ImagePipelineService.Variant> variantFor(
            List<ImagePipelineService.Variant> variants, String size, String format) {
        return variants.stream()
                .filter(v -> v.size().equals(size) && v.format().equals(format))
                .findFirst();
    }
}
