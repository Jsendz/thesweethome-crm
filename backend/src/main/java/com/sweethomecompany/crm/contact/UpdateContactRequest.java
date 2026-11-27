package com.sweethomecompany.crm.contact;

import java.util.UUID;

/**
 * Every field is optional: omitting a field means "leave it unchanged,"
 * same PATCH semantics as UpdatePropertyRequest/UpdateUserRequest.
 */
public record UpdateContactRequest(
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
