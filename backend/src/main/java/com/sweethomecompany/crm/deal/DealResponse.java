package com.sweethomecompany.crm.deal;

import com.sweethomecompany.crm.contact.Contact;
import com.sweethomecompany.crm.property.Property;
import com.sweethomecompany.crm.user.AppUser;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record DealResponse(
        UUID id,
        UUID contactId,
        String contactName,
        UUID propertyId,
        String propertyReferenceCode,
        UUID assignedUserId,
        String assignedUserFullName,
        DealStage stage,
        String lostReason,
        String coldReason,
        UUID coldMarkedByUserId,
        Instant coldMarkedAt,
        LocalDate expectedCloseDate,
        Instant createdAt,
        Instant updatedAt) {

    // property may be null (propertyId is optional on a deal); contact and
    // assignedUser are never null - a deal always has both.
    public static DealResponse from(Deal deal, Contact contact, Property property, AppUser assignedUser) {
        return new DealResponse(
                deal.getId(),
                deal.getContactId(),
                contact.getFirstName() + " " + contact.getLastName(),
                deal.getPropertyId(),
                property != null ? property.getReferenceCode() : null,
                deal.getAssignedUserId(),
                assignedUser.getFullName(),
                deal.getStage(),
                deal.getLostReason(),
                deal.getColdReason(),
                deal.getColdMarkedByUserId(),
                deal.getColdMarkedAt(),
                deal.getExpectedCloseDate(),
                deal.getCreatedAt(),
                deal.getUpdatedAt());
    }
}
