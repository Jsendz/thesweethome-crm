package com.sweethomecompany.crm.deal;

import java.time.Instant;
import java.util.UUID;

public record ActivityResponse(
        UUID id,
        UUID dealId,
        UUID contactId,
        UUID propertyId,
        UUID userId,
        String userFullName,
        ActivityType activityType,
        ActivityStatus status,
        String notes,
        Instant occurredAt,
        Instant createdAt) {

    public static ActivityResponse from(Activity activity, String userFullName) {
        return new ActivityResponse(
                activity.getId(),
                activity.getDealId(),
                activity.getContactId(),
                activity.getPropertyId(),
                activity.getUserId(),
                userFullName,
                activity.getActivityType(),
                activity.getStatus(),
                activity.getNotes(),
                activity.getOccurredAt(),
                activity.getCreatedAt());
    }
}
