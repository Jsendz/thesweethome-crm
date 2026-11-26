package com.sweethomecompany.crm.property;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PropertyTranslationRepository extends JpaRepository<PropertyTranslation, UUID> {

    List<PropertyTranslation> findByPropertyId(UUID propertyId);

    Optional<PropertyTranslation> findByPropertyIdAndLocale(UUID propertyId, String locale);
}
