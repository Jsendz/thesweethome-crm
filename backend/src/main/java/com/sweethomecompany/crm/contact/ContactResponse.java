package com.sweethomecompany.crm.contact;

import java.time.Instant;
import java.util.UUID;

// No embedded deals list - GET /api/deals?contactId= is how the frontend
// fetches a contact's deals (a deliberate decoupling from this response).
public record ContactResponse(
        UUID id,
        ContactType contactType,
        String firstName,
        String lastName,
        String email,
        String phone,
        String preferredLocale,
        ContactSource source,
        String sourceDetail,
        String notes,
        UUID ownerUserId,
        Instant createdAt,
        Instant updatedAt) {

    public static ContactResponse from(Contact contact) {
        return new ContactResponse(
                contact.getId(),
                contact.getContactType(),
                contact.getFirstName(),
                contact.getLastName(),
                contact.getEmail(),
                contact.getPhone(),
                contact.getPreferredLocale(),
                contact.getSource(),
                contact.getSourceDetail(),
                contact.getNotes(),
                contact.getOwnerUserId(),
                contact.getCreatedAt(),
                contact.getUpdatedAt());
    }
}
