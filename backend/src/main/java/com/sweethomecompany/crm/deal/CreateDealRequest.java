package com.sweethomecompany.crm.deal;

import java.time.LocalDate;
import java.util.UUID;

/**
 * contactId is required (400 if null; 404 if it doesn't resolve).
 * propertyId/assignedUserId are optional - 404 if provided but unresolvable.
 * assignedUserId defaults to the acting user's id when omitted. stage is
 * always LEAD at creation and is not settable here.
 */
public record CreateDealRequest(
        UUID contactId,
        UUID propertyId,
        UUID assignedUserId,
        LocalDate expectedCloseDate) {
}
