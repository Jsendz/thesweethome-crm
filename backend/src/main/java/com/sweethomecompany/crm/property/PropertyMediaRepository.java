package com.sweethomecompany.crm.property;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface PropertyMediaRepository extends JpaRepository<PropertyMedia, UUID> {

    List<PropertyMedia> findByPropertyIdOrderBySortOrder(UUID propertyId);
}
