package com.sweethomecompany.crm.deal;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Every field is optional: omitting a field means "leave it unchanged,"
 * same PATCH semantics as UpdatePropertyRequest/UpdateContactRequest.
 * stage gets special handling in DealController when it's COLD or
 * CLOSED_LOST - see the controller for the coldReason/lostReason rules.
 */
public record UpdateDealRequest(
        UUID propertyId,
        UUID assignedUserId,
        DealStage stage,
        String coldReason,
        String lostReason,
        LocalDate expectedCloseDate) {
}
