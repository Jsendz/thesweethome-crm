package com.sweethomecompany.crm.deal;

import java.time.Instant;

/**
 * activityType and occurredAt are required (400 if either is null).
 * status is optional: when omitted, DealController defaults it to
 * ActivityStatus.SCHEDULED if activityType == VIEWING, else COMPLETED.
 */
public record CreateActivityRequest(
        ActivityType activityType,
        String notes,
        Instant occurredAt,
        ActivityStatus status) {
}
