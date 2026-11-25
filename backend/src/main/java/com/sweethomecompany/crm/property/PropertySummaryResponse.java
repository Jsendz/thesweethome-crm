package com.sweethomecompany.crm.property;

import java.math.BigDecimal;
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
     * thumbnailUrl is always null until Task 4 adds property_media and
     * updates this mapping to pull the first photo's thumbnail variant.
     */
    public static PropertySummaryResponse from(Property property) {
        return new PropertySummaryResponse(
                property.getId(),
                property.getReferenceCode(),
                property.getPropertyType(),
                property.getOperation(),
                property.getStatus(),
                property.getPrice(),
                null);
    }
}
