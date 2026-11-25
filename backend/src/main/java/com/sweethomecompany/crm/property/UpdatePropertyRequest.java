package com.sweethomecompany.crm.property;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Every field is optional: omitting a field means "leave it unchanged,"
 * same PATCH semantics UpdateUserRequest established in Phase 2. Setting
 * status to WITHDRAWN is how a listing is withdrawn - there's no separate
 * delete endpoint. caTitle/caDescription, if sent, update the property's
 * ca translation (added/edited only through this endpoint, never through
 * POST /translations, which is for es/fr/en only).
 */
public record UpdatePropertyRequest(
        String referenceCode,
        PropertyType propertyType,
        Operation operation,
        PropertyStatus status,
        String addressLine,
        String parish,
        Double latitude,
        Double longitude,
        BigDecimal price,
        BigDecimal areaM2,
        Integer rooms,
        Integer bathrooms,
        Integer yearBuilt,
        String energyRating,
        Boolean isExclusive,
        LocalDate exclusivityEndDate,
        String caTitle,
        String caDescription) {
}
