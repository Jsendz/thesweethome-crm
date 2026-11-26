package com.sweethomecompany.crm.contact;

import java.util.UUID;

/**
 * Required (validated in ContactController, 400 if missing/blank):
 * contactType, firstName, lastName, source. ownerUserId is optional -
 * defaults to the acting user's id when omitted.
 */
public record CreateContactRequest(
        ContactType contactType,
        String firstName,
        String lastName,
        String email,
        String phone,
        String preferredLocale,
        ContactSource source,
        String sourceDetail,
        String notes,
        UUID ownerUserId) {
}
