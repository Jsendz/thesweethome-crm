package com.sweethomecompany.crm.audit;

import com.sweethomecompany.crm.user.AppUser;
import com.sweethomecompany.crm.user.AppUserRepository;
import com.sweethomecompany.crm.user.Role;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional
class AuditLogServiceTest {

    private static final UUID DEFAULT_AGENCY_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");

    @Autowired
    private AuditLogService auditLogService;

    @Autowired
    private AuditLogRepository auditLogRepository;

    @Autowired
    private AppUserRepository appUserRepository;

    @Test
    void writesAnAuditRowWithTheGivenFields() {
        AppUser actor = new AppUser();
        actor.setId(UUID.randomUUID());
        actor.setAgencyId(DEFAULT_AGENCY_ID);
        actor.setEmail("audit-log-actor@example.com");
        actor.setPasswordHash("hashed");
        actor.setFullName("Audit Actor");
        actor.setRole(Role.ADMIN);
        actor.setActive(true);
        actor.setCreatedAt(Instant.now());
        appUserRepository.save(actor);

        UUID entityId = UUID.randomUUID();
        auditLogService.log(DEFAULT_AGENCY_ID, actor.getId(), AuditAction.CREATE, "APP_USER", entityId, null);

        List<AuditLog> rows = auditLogRepository.findByActorUserId(actor.getId());
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).getAction()).isEqualTo(AuditAction.CREATE);
        assertThat(rows.get(0).getEntityType()).isEqualTo("APP_USER");
        assertThat(rows.get(0).getEntityId()).isEqualTo(entityId);
    }
}
