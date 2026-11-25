package com.sweethomecompany.crm.property;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface PropertyRepository extends JpaRepository<Property, UUID> {

    boolean existsByAgencyIdAndReferenceCode(UUID agencyId, String referenceCode);

    Optional<Property> findByAgencyIdAndReferenceCode(UUID agencyId, String referenceCode);
}
