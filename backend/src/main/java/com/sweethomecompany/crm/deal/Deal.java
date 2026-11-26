package com.sweethomecompany.crm.deal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "deal")
public class Deal {

    @Id
    private UUID id;

    @Column(name = "agency_id", nullable = false)
    private UUID agencyId;

    @Column(name = "contact_id", nullable = false)
    private UUID contactId;

    @Column(name = "property_id")
    private UUID propertyId;

    @Column(name = "assigned_user_id", nullable = false)
    private UUID assignedUserId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DealStage stage = DealStage.LEAD;

    @Column(name = "lost_reason")
    private String lostReason;

    @Column(name = "cold_reason")
    private String coldReason;

    @Column(name = "cold_marked_by_user_id")
    private UUID coldMarkedByUserId;

    @Column(name = "cold_marked_at")
    private Instant coldMarkedAt;

    @Column(name = "expected_close_date")
    private LocalDate expectedCloseDate;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public Deal() {
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

    public UUID getPropertyId() {
        return propertyId;
    }

    public void setPropertyId(UUID propertyId) {
        this.propertyId = propertyId;
    }

    public UUID getAssignedUserId() {
        return assignedUserId;
    }

    public void setAssignedUserId(UUID assignedUserId) {
        this.assignedUserId = assignedUserId;
    }

    public DealStage getStage() {
        return stage;
    }

    public void setStage(DealStage stage) {
        this.stage = stage;
    }

    public String getLostReason() {
        return lostReason;
    }

    public void setLostReason(String lostReason) {
        this.lostReason = lostReason;
    }

    public String getColdReason() {
        return coldReason;
    }

    public void setColdReason(String coldReason) {
        this.coldReason = coldReason;
    }

    public UUID getColdMarkedByUserId() {
        return coldMarkedByUserId;
    }

    public void setColdMarkedByUserId(UUID coldMarkedByUserId) {
        this.coldMarkedByUserId = coldMarkedByUserId;
    }

    public Instant getColdMarkedAt() {
        return coldMarkedAt;
    }

    public void setColdMarkedAt(Instant coldMarkedAt) {
        this.coldMarkedAt = coldMarkedAt;
    }

    public LocalDate getExpectedCloseDate() {
        return expectedCloseDate;
    }

    public void setExpectedCloseDate(LocalDate expectedCloseDate) {
        this.expectedCloseDate = expectedCloseDate;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
