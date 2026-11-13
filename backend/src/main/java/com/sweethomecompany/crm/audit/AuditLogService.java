package com.sweethomecompany.crm.audit;

import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;

@Service
public class AuditLogService {

    private final AuditLogRepository repository;

    public AuditLogService(AuditLogRepository repository) {
        this.repository = repository;
    }

    public void log(UUID agencyId, UUID actorUserId, AuditAction action, String entityType, UUID entityId, String detail) {
        AuditLog entry = new AuditLog();
        entry.setId(UUID.randomUUID());
        entry.setAgencyId(agencyId);
        entry.setActorUserId(actorUserId);
        entry.setAction(action);
        entry.setEntityType(entityType);
        entry.setEntityId(entityId);
        entry.setDetail(detail);
        entry.setOccurredAt(Instant.now());
        repository.save(entry);
    }
}
