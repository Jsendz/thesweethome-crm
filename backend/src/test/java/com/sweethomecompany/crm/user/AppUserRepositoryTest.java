package com.sweethomecompany.crm.user;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional
class AppUserRepositoryTest {

    private static final UUID DEFAULT_AGENCY_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");

    @Autowired
    private AppUserRepository repository;

    @Test
    void savesAndFindsByEmail() {
        AppUser user = new AppUser();
        user.setId(UUID.randomUUID());
        user.setAgencyId(DEFAULT_AGENCY_ID);
        user.setEmail("repo-test@example.com");
        user.setPasswordHash("hashed");
        user.setFullName("Repo Test");
        user.setRole(Role.AGENT);
        user.setActive(true);
        user.setCreatedAt(Instant.now());
        repository.save(user);

        AppUser found = repository.findByEmail("repo-test@example.com").orElseThrow();
        assertThat(found.getFullName()).isEqualTo("Repo Test");
        assertThat(found.getRole()).isEqualTo(Role.AGENT);
    }
}
