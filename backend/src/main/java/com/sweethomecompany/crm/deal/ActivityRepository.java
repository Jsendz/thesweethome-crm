package com.sweethomecompany.crm.deal;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ActivityRepository extends JpaRepository<Activity, UUID> {

    List<Activity> findByDealIdOrderByOccurredAtDesc(UUID dealId);
}
