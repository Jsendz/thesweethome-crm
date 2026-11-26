package com.sweethomecompany.crm.property;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

public record PropertySummaryResponse(
        UUID id,
        String referenceCode,
        PropertyType propertyType,
        Operation operation,
        PropertyStatus status,
        BigDecimal price,
        String thumbnailUrl) {

    /**
     * thumbnailUrl comes from the first photo (lowest sortOrder), preferring
     * its JPEG thumbnail variant and falling back to WebP if that's all a
     * particular upload has; null if the property has no media yet.
     */
    public static PropertySummaryResponse from(Property property, List<PropertyMedia> media) {
        String thumbnailUrl = media.stream()
                .min(Comparator.comparingInt(PropertyMedia::getSortOrder))
                .map(first -> {
                    var variants = first.getVariants();
                    String jpeg = variants.get("thumbnail_jpeg");
                    return jpeg != null ? jpeg : variants.get("thumbnail_webp");
                })
                .orElse(null);

        return new PropertySummaryResponse(
                property.getId(),
                property.getReferenceCode(),
                property.getPropertyType(),
                property.getOperation(),
                property.getStatus(),
                property.getPrice(),
                thumbnailUrl);
    }
}
