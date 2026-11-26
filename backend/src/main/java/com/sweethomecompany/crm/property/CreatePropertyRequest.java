package com.sweethomecompany.crm.property;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Required (validated in PropertyController, 400 if missing/blank):
 * referenceCode, propertyType, operation, price, caTitle. Everything else
 * is optional. caTitle/caDescription are inserted as the property's
 * mandatory Catalan (ca) translation in the same request - see the Phase 3
 * design doc's decision that creation is atomic with its ca translation.
 */
public record CreatePropertyRequest(
        String referenceCode,
        PropertyType propertyType,
        Operation operation,
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
        String caDescription,
        UUID ownerContactId) {
}
