package com.sweethomecompany.crm.deal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "activity")
public class Activity {

    @Id
    private UUID id;

    @Column(name = "agency_id", nullable = false)
    private UUID agencyId;

    @Column(name = "contact_id")
    private UUID contactId;

    @Column(name = "deal_id")
    private UUID dealId;

    @Column(name = "property_id")
    private UUID propertyId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "activity_type", nullable = false)
    private ActivityType activityType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ActivityStatus status;

    private String notes;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public Activity() {
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getAgencyId() {
        return agencyId;
    }

    public void setAgencyId(UUID agencyId) {
        this.agencyId = agencyId;
    }

    public UUID getContactId() {
        return contactId;
    }

    public void setContactId(UUID contactId) {
        this.contactId = contactId;
    }

    public UUID getDealId() {
        return dealId;
    }

    public void setDealId(UUID dealId) {
        this.dealId = dealId;
    }

    public UUID getPropertyId() {
        return propertyId;
    }

    public void setPropertyId(UUID propertyId) {
        this.propertyId = propertyId;
    }

    public UUID getUserId() {
        return userId;
    }

    public void setUserId(UUID userId) {
        this.userId = userId;
    }

    public ActivityType getActivityType() {
        return activityType;
    }

    public void setActivityType(ActivityType activityType) {
        this.activityType = activityType;
    }

    public ActivityStatus getStatus() {
        return status;
    }

    public void setStatus(ActivityStatus status) {
        this.status = status;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }

    public void setOccurredAt(Instant occurredAt) {
        this.occurredAt = occurredAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
