package com.sweethomecompany.crm.property;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record PropertyResponse(
        UUID id,
        String referenceCode,
        PropertyType propertyType,
        Operation operation,
        PropertyStatus status,
        String addressLine,
        String parish,
        String country,
        Double latitude,
        Double longitude,
        BigDecimal price,
        String currency,
        BigDecimal areaM2,
        Integer rooms,
        Integer bathrooms,
        Integer yearBuilt,
        String energyRating,
        boolean isExclusive,
        LocalDate exclusivityEndDate,
        List<TranslationResponse> translations,
        List<MediaResponse> media,
        Instant createdAt,
        Instant updatedAt) {

    public record TranslationResponse(String locale, String title, String description) {
    }

    public static PropertyResponse from(
            Property property, List<PropertyTranslation> translations, List<PropertyMedia> media) {
        return new PropertyResponse(
                property.getId(),
                property.getReferenceCode(),
                property.getPropertyType(),
                property.getOperation(),
                property.getStatus(),
                property.getAddressLine(),
                property.getParish(),
                property.getCountry(),
                property.getLatitude(),
                property.getLongitude(),
                property.getPrice(),
                property.getCurrency(),
                property.getAreaM2(),
                property.getRooms(),
                property.getBathrooms(),
                property.getYearBuilt(),
                property.getEnergyRating(),
                property.isExclusive(),
                property.getExclusivityEndDate(),
                translations.stream()
                        .map(t -> new TranslationResponse(t.getLocale(), t.getTitle(), t.getDescription()))
                        .toList(),
                media.stream().map(MediaResponse::from).toList(),
                property.getCreatedAt(),
                property.getUpdatedAt());
    }
}
